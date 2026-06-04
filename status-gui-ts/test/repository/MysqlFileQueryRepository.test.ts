import { GenericContainer, StartedTestContainer } from 'testcontainers';
import mysql from 'mysql2/promise';
import { MysqlFileQueryRepository } from '../../src/repository/MysqlFileQueryRepository';

jest.setTimeout(60000);

let container: StartedTestContainer;
let pool: mysql.Pool;
let repo: MysqlFileQueryRepository;

beforeAll(async () => {
  container = await new GenericContainer('mariadb:10.11')
    .withEnvironment({
      MARIADB_ROOT_PASSWORD: 'root',
      MARIADB_DATABASE: 'testdb',
      MARIADB_USER: 'test',
      MARIADB_PASSWORD: 'test',
    })
    .withExposedPorts(3306)
    .start();

  pool = mysql.createPool({
    host: container.getHost(),
    port: container.getMappedPort(3306),
    database: 'testdb',
    user: 'test',
    password: 'test',
    waitForConnections: true,
    timezone: '+00:00',
    supportBigNumbers: true,
  });

  await pool.query(`CREATE TABLE file_metadata (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    bucket VARCHAR(255) NOT NULL,
    report_id VARCHAR(255) NOT NULL,
    report_category VARCHAR(255) NOT NULL,
    object_key VARCHAR(2000) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(256) NULL,
    uploader_id VARCHAR(255) NOT NULL,
    tags TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    remark VARCHAR(1024) NULL,
    error_code VARCHAR(64) NULL,
    registered_at DATETIME(6) NOT NULL
  )`);

  await pool.query(`CREATE TABLE file_delivery (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_id VARCHAR(36) NOT NULL,
    consumer_id VARCHAR(255) NOT NULL,
    note TEXT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
  )`);

  repo = new MysqlFileQueryRepository(pool);
}, 60000);

afterAll(async () => {
  await pool.end();
  await container.stop();
});

async function insertFile(overrides: {
  id?: string; bucket?: string; uploaderId?: string; status?: string;
} = {}): Promise<string> {
  const id = overrides.id ?? crypto.randomUUID();
  const bucket = overrides.bucket ?? 'test-bucket';
  const uploaderId = overrides.uploaderId ?? 'uploader-01';
  const status = overrides.status ?? 'REGISTERED';
  const now = new Date().toISOString().replace('T', ' ').slice(0, 23);
  await pool.query(
    `INSERT INTO file_metadata
       (id, bucket, report_id, report_category, object_key, filename, content_type, file_size, uploader_id, status, registered_at)
     VALUES (?, ?, 'r1', 'cat1', ?, ?, 'text/csv', 1024, ?, ?, ?)`,
    [id, bucket, `key/${id}`, `file-${id}.csv`, uploaderId, status, now],
  );
  return id;
}

async function insertDelivery(fileId: string, consumerId: string): Promise<void> {
  const id = crypto.randomUUID();
  const now = new Date().toISOString().replace('T', ' ').slice(0, 23);
  await pool.query(
    'INSERT INTO file_delivery (id, file_id, consumer_id, processed_at) VALUES (?, ?, ?, ?)',
    [id, fileId, consumerId, now],
  );
}

test('search returns matching rows', async () => {
  const uploaderId = `up-search-${crypto.randomUUID()}`;
  const id = await insertFile({ uploaderId });
  const result = await repo.search({ uploaderId, page: 0 });
  expect(result.rows.some(r => r.id === id)).toBe(true);
});

test('search filters by bucket', async () => {
  await insertFile({ bucket: 'bucket-ts-a' });
  await insertFile({ bucket: 'bucket-ts-b' });
  const result = await repo.search({ bucket: 'bucket-ts-a', page: 0 });
  expect(result.rows.every(r => r.bucket === 'bucket-ts-a')).toBe(true);
});

test('search filters by status', async () => {
  const uploaderId = `up-status-${crypto.randomUUID()}`;
  await insertFile({ uploaderId, status: 'FAILED' });
  await insertFile({ uploaderId, status: 'REGISTERED' });
  const result = await repo.search({ uploaderId, status: 'FAILED', page: 0 });
  expect(result.rows).toHaveLength(1);
  expect(result.rows[0].status).toBe('FAILED');
});

test('search total reflects full count not page size', async () => {
  const uploaderId = `up-total-${crypto.randomUUID()}`;
  for (let i = 0; i < 5; i++) await insertFile({ uploaderId });
  const result = await repo.search({ uploaderId, page: 0, size: 2 });
  expect(result.rows).toHaveLength(2);
  expect(result.total).toBe(5);
});

test('findById returns file when it exists', async () => {
  const id = await insertFile();
  const row = await repo.findById(id);
  expect(row).not.toBeNull();
  expect(row!.id).toBe(id);
});

test('findById returns null for unknown id', async () => {
  expect(await repo.findById('no-such-id')).toBeNull();
});

test('findDeliveries returns delivery records for file', async () => {
  const fileId = await insertFile();
  await insertDelivery(fileId, 'consumer-a');
  await insertDelivery(fileId, 'consumer-b');
  const deliveries = await repo.findDeliveries(fileId);
  expect(deliveries).toHaveLength(2);
  expect(deliveries.map(d => d.consumerId)).toContain('consumer-a');
});

test('findMissing returns files not delivered to consumer', async () => {
  const consumer = `consumer-missing-${crypto.randomUUID()}`;
  const deliveredId = await insertFile();
  const missingId = await insertFile();
  await insertDelivery(deliveredId, consumer);
  const since = new Date(Date.now() - 3600 * 1000);
  const result = await repo.findMissing({ consumerId: consumer, since, page: 0 });
  const ids = result.rows.map(r => r.id);
  expect(ids).toContain(missingId);
  expect(ids).not.toContain(deliveredId);
});
