# Status Query GUI (TypeScript) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Node.js/Express web app (`status-gui-ts/`) that gives ops/support a browser UI to search files, view file detail, and find files a consumer hasn't processed — reading directly from MariaDB with EJS server-side rendering.

**Architecture:** Express app factory (`createApp(repo, oidcConfig?)`) accepts a `FileQueryRepository` interface, enabling supertest route tests with a `FakeFileQueryRepository` and no database. Production wires in `MysqlFileQueryRepository` backed by a `mysql2` pool. EJS templates include `_head.ejs`/`_foot.ejs` partials for the shared nav and CSS. OIDC (express-openid-connect) is installed only when all four OIDC env vars are present.

**Tech Stack:** Node.js 20+, TypeScript 5.6, Express 4.21, EJS 3.1, mysql2 3.11, express-openid-connect 2.17, Jest 29 + supertest, testcontainers 10

---

## File Map

| File | Responsibility |
|---|---|
| `status-gui-ts/package.json` | npm scripts, dependencies |
| `status-gui-ts/tsconfig.json` | TypeScript config |
| `status-gui-ts/jest.config.ts` | Jest with ts-jest |
| `src/repository/types.ts` | `FileRow`, `DeliveryRow`, `PagedResult`, `FileQueryRepository` interface |
| `src/repository/MysqlFileQueryRepository.ts` | Raw SQL queries via mysql2 |
| `src/app.ts` | `createApp(repo, oidcConfig?)` — Express factory, no env var imports |
| `src/routes/fileSearch.ts` | `GET /`, `GET /files`, `GET /files/:id` |
| `src/routes/missing.ts` | `GET /missing` |
| `src/views/_head.ejs` | HTML head + nav + inline CSS (receives `title`, `activeTab`) |
| `src/views/_foot.ejs` | Closing `</div></body></html>` |
| `src/views/fileSearch.ejs` | Search form + results table |
| `src/views/fileDetail.ejs` | Metadata grid + delivery records |
| `src/views/missing.ejs` | Missing files form + results |
| `src/views/error.ejs` | 404 / 500 error page |
| `src/config.ts` | Env var loading (throws on missing DB vars) |
| `src/db.ts` | mysql2 pool from config |
| `src/index.ts` | Entry point — wires repo + app + listen |
| `test/FakeFileQueryRepository.ts` | Fake + `sampleFileRow` + `sampleDeliveryRow` helpers |
| `test/routes/fileSearch.test.ts` | supertest route tests |
| `test/routes/missing.test.ts` | supertest route tests |
| `test/repository/MysqlFileQueryRepository.test.ts` | Testcontainers MariaDB integration tests |

---

## Task 1: Project Scaffold

**Files:**
- Create: `status-gui-ts/package.json`
- Create: `status-gui-ts/tsconfig.json`
- Create: `status-gui-ts/jest.config.ts`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p status-gui-ts/src/repository
mkdir -p status-gui-ts/src/routes
mkdir -p status-gui-ts/src/views
mkdir -p status-gui-ts/test/routes
mkdir -p status-gui-ts/test/repository
```

- [ ] **Step 2: Create `status-gui-ts/package.json`**

```json
{
  "name": "status-gui-ts",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc",
    "postbuild": "cp -r src/views dist/",
    "start": "node dist/index.js",
    "test": "jest"
  },
  "dependencies": {
    "dotenv": "^16.4.5",
    "ejs": "^3.1.10",
    "express": "^4.21.0",
    "express-openid-connect": "^2.17.1",
    "mysql2": "^3.11.0"
  },
  "devDependencies": {
    "@types/ejs": "^3.1.5",
    "@types/express": "^4.17.21",
    "@types/jest": "^29.5.13",
    "@types/node": "^22.0.0",
    "@types/supertest": "^6.0.2",
    "jest": "^29.7.0",
    "supertest": "^7.0.0",
    "testcontainers": "^10.13.0",
    "ts-jest": "^29.2.5",
    "tsx": "^4.19.1",
    "typescript": "^5.6.0"
  }
}
```

- [ ] **Step 3: Create `status-gui-ts/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "commonjs",
    "lib": ["ES2022"],
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "resolveJsonModule": true
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist", "test"]
}
```

- [ ] **Step 4: Create `status-gui-ts/jest.config.ts`**

```typescript
import type { Config } from 'jest';

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/test'],
  testMatch: ['**/*.test.ts'],
  testTimeout: 60000,
};

export default config;
```

- [ ] **Step 5: Install dependencies**

```bash
cd status-gui-ts && npm install
```

Expected: `node_modules/` created, no errors.

- [ ] **Step 6: Verify TypeScript compiler works**

Create a minimal placeholder `src/index.ts`:

```typescript
export {};
```

Then run:

```bash
cd status-gui-ts && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add status-gui-ts/
git commit -m "feat(status-gui-ts): scaffold Node.js/Express project"
```

---

## Task 2: Types, Config, DB, and FakeFileQueryRepository

**Files:**
- Create: `status-gui-ts/src/repository/types.ts`
- Create: `status-gui-ts/src/config.ts`
- Create: `status-gui-ts/src/db.ts`
- Create: `status-gui-ts/test/FakeFileQueryRepository.ts`

No tests for this task — these are data types and infrastructure used by subsequent tasks.

- [ ] **Step 1: Create `src/repository/types.ts`**

```typescript
export interface FileRow {
  id: string;
  bucket: string;
  reportId: string;
  reportCategory: string;
  objectKey: string;
  filename: string;
  contentType: string;
  fileSize: number;
  checksum: string | null;
  uploaderId: string;
  tags: string | null;
  status: string;
  remark: string | null;
  errorCode: string | null;
  registeredAt: Date;
}

export interface DeliveryRow {
  id: string;
  fileId: string;
  consumerId: string;
  note: string | null;
  processedAt: Date;
}

export interface PagedResult {
  rows: FileRow[];
  total: number;
}

export interface FileQueryRepository {
  search(params: {
    uploaderId?: string;
    bucket?: string;
    status?: string;
    since?: Date;
    page: number;
    size?: number;
  }): Promise<PagedResult>;

  findById(id: string): Promise<FileRow | null>;

  findDeliveries(fileId: string): Promise<DeliveryRow[]>;

  findMissing(params: {
    consumerId: string;
    bucket?: string;
    since: Date;
    page: number;
    size?: number;
  }): Promise<PagedResult>;
}
```

- [ ] **Step 2: Create `src/config.ts`**

Note: `config.ts` throws at import time if required DB env vars are missing. It is only imported by `db.ts` and `index.ts` — never by `app.ts`, so route tests are unaffected.

```typescript
import 'dotenv/config';

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

const issuerBaseURL = process.env.OIDC_ISSUER_BASE_URL;
const clientID = process.env.OIDC_CLIENT_ID;
const clientSecret = process.env.OIDC_CLIENT_SECRET;
const baseURL = process.env.OIDC_BASE_URL;

export const config = {
  db: {
    host: required('DB_HOST'),
    port: parseInt(process.env.DB_PORT ?? '3306', 10),
    database: required('DB_NAME'),
    user: required('DB_USER'),
    password: required('DB_PASS'),
  },
  port: parseInt(process.env.GUI_PORT ?? '8091', 10),
  oidc:
    issuerBaseURL && clientID && clientSecret && baseURL
      ? { enabled: true as const, issuerBaseURL, clientID, clientSecret, baseURL }
      : { enabled: false as const },
};
```

- [ ] **Step 3: Create `src/db.ts`**

```typescript
import mysql from 'mysql2/promise';
import { config } from './config';

export const pool = mysql.createPool({
  host: config.db.host,
  port: config.db.port,
  database: config.db.database,
  user: config.db.user,
  password: config.db.password,
  connectionLimit: 5,
  timezone: '+00:00',
});
```

- [ ] **Step 4: Create `test/FakeFileQueryRepository.ts`**

```typescript
import { FileQueryRepository, FileRow, DeliveryRow, PagedResult } from '../src/repository/types';

export function sampleFileRow(overrides: Partial<FileRow> = {}): FileRow {
  return {
    id: 'test-id-001',
    bucket: 'finance',
    reportId: 'r1',
    reportCategory: 'cat1',
    objectKey: 'finance/report_Q1.csv',
    filename: 'report_Q1.csv',
    contentType: 'text/csv',
    fileSize: 48320,
    checksum: null,
    uploaderId: 'uploader-01',
    tags: null,
    status: 'REGISTERED',
    remark: null,
    errorCode: null,
    registeredAt: new Date('2026-06-03T09:12:34Z'),
    ...overrides,
  };
}

export function sampleDeliveryRow(overrides: Partial<DeliveryRow> = {}): DeliveryRow {
  return {
    id: 'delivery-001',
    fileId: 'test-id-001',
    consumerId: 'consumer-reporting',
    note: null,
    processedAt: new Date('2026-06-03T09:15:02Z'),
    ...overrides,
  };
}

export class FakeFileQueryRepository implements FileQueryRepository {
  searchResult: PagedResult = { rows: [], total: 0 };
  findByIdResult: FileRow | null = null;
  findDeliveriesResult: DeliveryRow[] = [];
  findMissingResult: PagedResult = { rows: [], total: 0 };

  async search(): Promise<PagedResult> { return this.searchResult; }
  async findById(): Promise<FileRow | null> { return this.findByIdResult; }
  async findDeliveries(): Promise<DeliveryRow[]> { return this.findDeliveriesResult; }
  async findMissing(): Promise<PagedResult> { return this.findMissingResult; }
}
```

- [ ] **Step 5: Verify TypeScript compiles**

```bash
cd status-gui-ts && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add status-gui-ts/src/ status-gui-ts/test/FakeFileQueryRepository.ts
git commit -m "feat(status-gui-ts): add types, config, db pool, and fake repository"
```

---

## Task 3: MysqlFileQueryRepository with Integration Tests

**Files:**
- Create: `status-gui-ts/src/repository/MysqlFileQueryRepository.ts`
- Test: `status-gui-ts/test/repository/MysqlFileQueryRepository.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `test/repository/MysqlFileQueryRepository.test.ts`:

```typescript
import { GenericContainer, StartedTestContainer } from 'testcontainers';
import mysql from 'mysql2/promise';
import { MysqlFileQueryRepository } from '../../src/repository/MysqlFileQueryRepository';

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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd status-gui-ts && npx jest test/repository --testNamePattern="." 2>&1 | tail -5
```

Expected: compilation error — `MysqlFileQueryRepository` does not exist yet.

- [ ] **Step 3: Create `src/repository/MysqlFileQueryRepository.ts`**

```typescript
import { Pool, RowDataPacket } from 'mysql2/promise';
import { FileQueryRepository, FileRow, DeliveryRow, PagedResult } from './types';

function toFileRow(row: RowDataPacket): FileRow {
  return {
    id: row['id'] as string,
    bucket: row['bucket'] as string,
    reportId: row['report_id'] as string,
    reportCategory: row['report_category'] as string,
    objectKey: row['object_key'] as string,
    filename: row['filename'] as string,
    contentType: row['content_type'] as string,
    fileSize: Number(row['file_size']),
    checksum: (row['checksum'] as string | null) ?? null,
    uploaderId: row['uploader_id'] as string,
    tags: (row['tags'] as string | null) ?? null,
    status: row['status'] as string,
    remark: (row['remark'] as string | null) ?? null,
    errorCode: (row['error_code'] as string | null) ?? null,
    registeredAt: row['registered_at'] as Date,
  };
}

function toDeliveryRow(row: RowDataPacket): DeliveryRow {
  return {
    id: row['id'] as string,
    fileId: row['file_id'] as string,
    consumerId: row['consumer_id'] as string,
    note: (row['note'] as string | null) ?? null,
    processedAt: row['processed_at'] as Date,
  };
}

export class MysqlFileQueryRepository implements FileQueryRepository {
  constructor(private readonly pool: Pool) {}

  async search({ uploaderId, bucket, status, since, page, size = 20 }: {
    uploaderId?: string;
    bucket?: string;
    status?: string;
    since?: Date;
    page: number;
    size?: number;
  }): Promise<PagedResult> {
    const conditions: string[] = [];
    const params: unknown[] = [];
    if (uploaderId) { conditions.push('uploader_id = ?'); params.push(uploaderId); }
    if (bucket) { conditions.push('bucket = ?'); params.push(bucket); }
    if (status) { conditions.push('status = ?'); params.push(status); }
    if (since) { conditions.push('registered_at >= ?'); params.push(since); }

    const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
    const [countRows] = await this.pool.query(
      `SELECT COUNT(*) AS total FROM file_metadata ${where}`, params,
    ) as [Array<{ total: number }>, unknown];
    const total = Number(countRows[0].total);

    const [rows] = await this.pool.query<RowDataPacket[]>(
      `SELECT * FROM file_metadata ${where} ORDER BY registered_at DESC LIMIT ? OFFSET ?`,
      [...params, size, page * size],
    );
    return { rows: rows.map(toFileRow), total };
  }

  async findById(id: string): Promise<FileRow | null> {
    const [rows] = await this.pool.query<RowDataPacket[]>(
      'SELECT * FROM file_metadata WHERE id = ?', [id],
    );
    return rows.length ? toFileRow(rows[0]) : null;
  }

  async findDeliveries(fileId: string): Promise<DeliveryRow[]> {
    const [rows] = await this.pool.query<RowDataPacket[]>(
      'SELECT * FROM file_delivery WHERE file_id = ?', [fileId],
    );
    return rows.map(toDeliveryRow);
  }

  async findMissing({ consumerId, bucket, since, page, size = 20 }: {
    consumerId: string;
    bucket?: string;
    since: Date;
    page: number;
    size?: number;
  }): Promise<PagedResult> {
    const conditions = ['fd.id IS NULL', 'fm.registered_at >= ?'];
    const params: unknown[] = [consumerId, since];
    if (bucket) { conditions.push('fm.bucket = ?'); params.push(bucket); }

    const where = `WHERE ${conditions.join(' AND ')}`;
    const join = 'LEFT JOIN file_delivery fd ON fd.file_id = fm.id AND fd.consumer_id = ?';

    const [countRows] = await this.pool.query(
      `SELECT COUNT(*) AS total FROM file_metadata fm ${join} ${where}`, params,
    ) as [Array<{ total: number }>, unknown];
    const total = Number(countRows[0].total);

    const [rows] = await this.pool.query<RowDataPacket[]>(
      `SELECT fm.* FROM file_metadata fm ${join} ${where} ORDER BY fm.registered_at DESC LIMIT ? OFFSET ?`,
      [...params, size, page * size],
    );
    return { rows: rows.map(toFileRow), total };
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd status-gui-ts && npx jest test/repository
```

Expected: `Tests: 8 passed`

- [ ] **Step 5: Commit**

```bash
git add status-gui-ts/src/repository/MysqlFileQueryRepository.ts \
        status-gui-ts/test/repository/MysqlFileQueryRepository.test.ts
git commit -m "feat(status-gui-ts): add MysqlFileQueryRepository with integration tests"
```

---

## Task 4: EJS Views

**Files:**
- Create: `status-gui-ts/src/views/_head.ejs`
- Create: `status-gui-ts/src/views/_foot.ejs`
- Create: `status-gui-ts/src/views/fileSearch.ejs`
- Create: `status-gui-ts/src/views/fileDetail.ejs`
- Create: `status-gui-ts/src/views/missing.ejs`
- Create: `status-gui-ts/src/views/error.ejs`

No tests for this task — views are verified through route tests in Tasks 5 and 6.

- [ ] **Step 1: Create `src/views/_head.ejs`**

Receives locals: `title` (string), `activeTab` (string — `'search'`, `'missing'`, or `''`).

```ejs
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>File Hub — <%= title %></title>
  <style>
    body{font-family:sans-serif;margin:0;color:#333}
    nav{background:#1565c0;padding:10px 20px;display:flex;gap:24px}
    nav a{color:white;text-decoration:none;padding-bottom:2px}
    nav a.active{border-bottom:2px solid white}
    .container{max-width:1200px;margin:0 auto;padding:20px}
    .filter-bar{background:#f5f5f5;padding:12px 16px;display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:16px;border-radius:4px}
    .filter-bar label{display:flex;flex-direction:column;font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666;gap:4px}
    input,select{padding:5px 8px;border:1px solid #ccc;border-radius:3px;font-size:.9rem}
    button{padding:6px 16px;background:#1565c0;color:white;border:none;border-radius:3px;cursor:pointer;font-size:.9rem}
    table{width:100%;border-collapse:collapse}
    th{background:#e3f2fd;text-align:left;padding:8px 12px;font-size:.85rem}
    td{padding:7px 12px;font-size:.85rem;border-top:1px solid #eee}
    tr:nth-child(even) td{background:#fafafa}
    .badge-registered{background:#e8f5e9;color:#2e7d32;padding:2px 8px;border-radius:10px;font-size:.8rem}
    .badge-failed{background:#ffebee;color:#c62828;padding:2px 8px;border-radius:10px;font-size:.8rem}
    .pagination{margin-top:12px;font-size:.85rem;color:#666}
    .pagination a{color:#1565c0;margin:0 6px}
    .detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 24px;margin-bottom:20px}
    .detail-label{font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666}
    h2{margin-bottom:16px}
    a.back-link{color:#1565c0;font-size:.85rem;display:inline-block;margin-top:12px}
    .error-msg{color:#c62828;background:#ffebee;padding:10px;border-radius:4px;margin-bottom:12px}
    .empty-msg{color:#666;padding:40px;text-align:center}
  </style>
</head>
<body>
  <nav>
    <a href="/files"<% if (activeTab === 'search') { %> class="active"<% } %>>File Search</a>
    <a href="/missing"<% if (activeTab === 'missing') { %> class="active"<% } %>>Missing Files</a>
  </nav>
  <div class="container">
```

- [ ] **Step 2: Create `src/views/_foot.ejs`**

```ejs
  </div>
</body>
</html>
```

- [ ] **Step 3: Create `src/views/error.ejs`**

Receives locals: `message` (string).

```ejs
<%- include('_head', { title: 'Error', activeTab: '' }) %>
<p class="error-msg"><%= message %></p>
<%- include('_foot') %>
```

- [ ] **Step 4: Create `src/views/fileSearch.ejs`**

Receives locals: `rows` (FileRow[]), `total` (number), `page` (number), `uploaderId` (string), `bucket` (string), `status` (string), `since` (string), `prevUrl` (string|null), `nextUrl` (string|null).

```ejs
<%- include('_head', { title: 'File Search', activeTab: 'search' }) %>
<h2>File Search</h2>
<form action="/files" method="get" class="filter-bar">
  <label>Uploader ID
    <input type="text" name="uploaderId" placeholder="any" value="<%= uploaderId %>">
  </label>
  <label>Bucket
    <input type="text" name="bucket" placeholder="any" value="<%= bucket %>">
  </label>
  <label>Status
    <select name="status">
      <option value="">any</option>
      <option value="REGISTERED"<%= status === 'REGISTERED' ? ' selected' : '' %>>REGISTERED</option>
      <option value="FAILED"<%= status === 'FAILED' ? ' selected' : '' %>>FAILED</option>
    </select>
  </label>
  <label>Since
    <input type="date" name="since" value="<%= since %>">
  </label>
  <button type="submit">Search</button>
</form>
<% if (rows.length === 0) { %>
  <p class="empty-msg">No files found.</p>
<% } else { %>
  <table>
    <thead>
      <tr>
        <th>ID</th><th>Filename</th><th>Bucket</th><th>Uploader</th><th>Status</th><th>Registered At</th>
      </tr>
    </thead>
    <tbody>
      <% rows.forEach(function(row) { %>
      <tr>
        <td><a href="/files/<%= row.id %>"><%= row.id.slice(0, 8) %>…</a></td>
        <td><%= row.filename %></td>
        <td><%= row.bucket %></td>
        <td><%= row.uploaderId %></td>
        <td><span class="badge-<%= row.status === 'FAILED' ? 'failed' : 'registered' %>"><%= row.status %></span></td>
        <td><%= row.registeredAt.toISOString().replace('T', ' ').slice(0, 16) %></td>
      </tr>
      <% }); %>
    </tbody>
  </table>
  <div class="pagination">
    Showing <%= page * 20 + 1 %>–<%= Math.min((page + 1) * 20, total) %> of <%= total %>
    <% if (prevUrl) { %><a href="<%= prevUrl %>">← Prev</a><% } %>
    <% if (nextUrl) { %><a href="<%= nextUrl %>">Next →</a><% } %>
  </div>
<% } %>
<%- include('_foot') %>
```

- [ ] **Step 5: Create `src/views/fileDetail.ejs`**

Receives locals: `file` (FileRow), `deliveries` (DeliveryRow[]).

```ejs
<%- include('_head', { title: 'File Detail', activeTab: 'search' }) %>
<a href="/files" class="back-link">← Back to search</a>
<h2>File: <%= file.filename %></h2>
<div class="detail-grid">
  <div><div class="detail-label">ID</div><div><%= file.id %></div></div>
  <div>
    <div class="detail-label">Status</div>
    <div><span class="badge-<%= file.status === 'FAILED' ? 'failed' : 'registered' %>"><%= file.status %></span></div>
  </div>
  <div><div class="detail-label">Filename</div><div><%= file.filename %></div></div>
  <div><div class="detail-label">Bucket</div><div><%= file.bucket %></div></div>
  <div><div class="detail-label">Report ID</div><div><%= file.reportId %></div></div>
  <div><div class="detail-label">Report Category</div><div><%= file.reportCategory %></div></div>
  <div><div class="detail-label">Uploader</div><div><%= file.uploaderId %></div></div>
  <div><div class="detail-label">File Size</div><div><%= file.fileSize %> bytes</div></div>
  <div><div class="detail-label">Content Type</div><div><%= file.contentType %></div></div>
  <div><div class="detail-label">Checksum</div><div><%= file.checksum || '—' %></div></div>
  <div><div class="detail-label">Object Key</div><div><%= file.objectKey %></div></div>
  <div><div class="detail-label">Tags</div><div><%= file.tags || '—' %></div></div>
  <div><div class="detail-label">Remark</div><div><%= file.remark || '—' %></div></div>
  <div><div class="detail-label">Error Code</div><div><%= file.errorCode || '—' %></div></div>
  <div><div class="detail-label">Registered At</div><div><%= file.registeredAt.toISOString().replace('T', ' ').slice(0, 19) %></div></div>
</div>
<h3>Delivery Records</h3>
<% if (deliveries.length === 0) { %>
  <p class="empty-msg">No consumer has processed this file yet.</p>
<% } else { %>
  <table>
    <thead><tr><th>Consumer</th><th>Processed At</th><th>Note</th></tr></thead>
    <tbody>
      <% deliveries.forEach(function(d) { %>
      <tr>
        <td><%= d.consumerId %></td>
        <td><%= d.processedAt.toISOString().replace('T', ' ').slice(0, 19) %></td>
        <td><%= d.note || '—' %></td>
      </tr>
      <% }); %>
    </tbody>
  </table>
<% } %>
<%- include('_foot') %>
```

- [ ] **Step 6: Create `src/views/missing.ejs`**

Receives locals: `rows` (FileRow[]), `total` (number), `page` (number), `consumerId` (string), `bucket` (string), `since` (string), `searched` (boolean), `prevUrl` (string|null), `nextUrl` (string|null).

```ejs
<%- include('_head', { title: 'Missing Files', activeTab: 'missing' }) %>
<h2>Missing Files</h2>
<form action="/missing" method="get" class="filter-bar">
  <label>Consumer ID *
    <input type="text" name="consumerId" placeholder="required" value="<%= consumerId %>">
  </label>
  <label>Bucket
    <input type="text" name="bucket" placeholder="any" value="<%= bucket %>">
  </label>
  <label>Since
    <input type="date" name="since" value="<%= since %>">
  </label>
  <button type="submit">Search</button>
</form>
<% if (!searched) { %>
  <p class="empty-msg">Enter a Consumer ID to find files not yet processed by that consumer.</p>
<% } else if (rows.length === 0) { %>
  <p class="empty-msg">No missing files for this consumer.</p>
<% } else { %>
  <table>
    <thead>
      <tr>
        <th>ID</th><th>Filename</th><th>Bucket</th><th>Uploader</th><th>Status</th><th>Registered At</th>
      </tr>
    </thead>
    <tbody>
      <% rows.forEach(function(row) { %>
      <tr>
        <td><a href="/files/<%= row.id %>"><%= row.id.slice(0, 8) %>…</a></td>
        <td><%= row.filename %></td>
        <td><%= row.bucket %></td>
        <td><%= row.uploaderId %></td>
        <td><span class="badge-<%= row.status === 'FAILED' ? 'failed' : 'registered' %>"><%= row.status %></span></td>
        <td><%= row.registeredAt.toISOString().replace('T', ' ').slice(0, 16) %></td>
      </tr>
      <% }); %>
    </tbody>
  </table>
  <div class="pagination">
    Showing <%= page * 20 + 1 %>–<%= Math.min((page + 1) * 20, total) %> of <%= total %>
    <% if (prevUrl) { %><a href="<%= prevUrl %>">← Prev</a><% } %>
    <% if (nextUrl) { %><a href="<%= nextUrl %>">Next →</a><% } %>
  </div>
<% } %>
<%- include('_foot') %>
```

- [ ] **Step 7: Commit**

```bash
git add status-gui-ts/src/views/
git commit -m "feat(status-gui-ts): add EJS views for all three pages"
```

---

## Task 5: App Factory + File Search Routes + Tests

**Files:**
- Create: `status-gui-ts/src/app.ts`
- Create: `status-gui-ts/src/routes/fileSearch.ts`
- Test: `status-gui-ts/test/routes/fileSearch.test.ts`

- [ ] **Step 1: Write the failing route tests**

Create `test/routes/fileSearch.test.ts`:

```typescript
import request from 'supertest';
import { createApp } from '../../src/app';
import { FakeFileQueryRepository, sampleFileRow, sampleDeliveryRow } from '../FakeFileQueryRepository';

describe('GET /', () => {
  it('redirects to /files', async () => {
    const app = createApp(new FakeFileQueryRepository());
    const res = await request(app).get('/');
    expect(res.status).toBe(302);
    expect(res.headers['location']).toBe('/files');
  });
});

describe('GET /files', () => {
  it('renders search form with results', async () => {
    const repo = new FakeFileQueryRepository();
    repo.searchResult = { rows: [sampleFileRow()], total: 1 };
    const res = await request(createApp(repo)).get('/files');
    expect(res.status).toBe(200);
    expect(res.text).toContain('report_Q1.csv');
    expect(res.text).toContain('uploader-01');
    expect(res.text).toContain('finance');
  });

  it('shows empty message when no results', async () => {
    const res = await request(createApp(new FakeFileQueryRepository())).get('/files');
    expect(res.status).toBe(200);
    expect(res.text).toContain('No files found');
  });

  it('shows FAILED badge for failed files', async () => {
    const repo = new FakeFileQueryRepository();
    repo.searchResult = { rows: [sampleFileRow({ status: 'FAILED' })], total: 1 };
    const res = await request(createApp(repo)).get('/files');
    expect(res.text).toContain('badge-failed');
  });

  it('shows pagination when results exceed page size', async () => {
    const repo = new FakeFileQueryRepository();
    repo.searchResult = { rows: [sampleFileRow()], total: 45 };
    const res = await request(createApp(repo)).get('/files');
    expect(res.text).toContain('Next');
    expect(res.text).toContain('45');
  });

  it('ignores malformed since parameter without crashing', async () => {
    const repo = new FakeFileQueryRepository();
    repo.searchResult = { rows: [], total: 0 };
    const res = await request(createApp(repo)).get('/files?since=notadate');
    expect(res.status).toBe(200);
  });
});

describe('GET /files/:id', () => {
  it('renders file detail page', async () => {
    const repo = new FakeFileQueryRepository();
    repo.findByIdResult = sampleFileRow();
    repo.findDeliveriesResult = [sampleDeliveryRow()];
    const res = await request(createApp(repo)).get('/files/test-id-001');
    expect(res.status).toBe(200);
    expect(res.text).toContain('test-id-001');
    expect(res.text).toContain('consumer-reporting');
  });

  it('returns 404 for unknown file', async () => {
    const res = await request(createApp(new FakeFileQueryRepository())).get('/files/no-such-id');
    expect(res.status).toBe(404);
    expect(res.text).toContain('File not found');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd status-gui-ts && npx jest test/routes/fileSearch --passWithNoTests 2>&1 | tail -5
```

Expected: compilation error — `createApp` does not exist yet.

- [ ] **Step 3: Create `src/routes/fileSearch.ts`**

```typescript
import { Router } from 'express';
import { FileQueryRepository } from '../repository/types';

function buildSearchUrl(params: {
  uploaderId?: string; bucket?: string; status?: string; since?: string; page?: number;
}): string {
  const parts: string[] = [];
  if (params.uploaderId) parts.push(`uploaderId=${encodeURIComponent(params.uploaderId)}`);
  if (params.bucket) parts.push(`bucket=${encodeURIComponent(params.bucket)}`);
  if (params.status) parts.push(`status=${encodeURIComponent(params.status)}`);
  if (params.since) parts.push(`since=${params.since}`);
  if (params.page !== undefined && params.page > 0) parts.push(`page=${params.page}`);
  return '/files' + (parts.length ? '?' + parts.join('&') : '');
}

export function fileSearchRouter(repo: FileQueryRepository): Router {
  const router = Router();

  router.get('/', (_req, res) => res.redirect('/files'));

  router.get('/files', async (req, res, next) => {
    try {
      const uploaderId = ((req.query['uploaderId'] as string) ?? '').trim() || undefined;
      const bucket = ((req.query['bucket'] as string) ?? '').trim() || undefined;
      const status = ((req.query['status'] as string) ?? '').trim() || undefined;
      const sinceStr = ((req.query['since'] as string) ?? '').trim() || undefined;
      const page = Math.max(0, parseInt((req.query['page'] as string) ?? '0', 10) || 0);

      let since: Date | undefined;
      if (sinceStr) {
        const d = new Date(sinceStr);
        if (!isNaN(d.getTime())) since = d;
      }

      const result = await repo.search({ uploaderId, bucket, status, since, page });
      const prevUrl = page > 0
        ? buildSearchUrl({ uploaderId, bucket, status, since: sinceStr, page: page - 1 })
        : null;
      const nextUrl = (page + 1) * 20 < result.total
        ? buildSearchUrl({ uploaderId, bucket, status, since: sinceStr, page: page + 1 })
        : null;

      res.render('fileSearch', {
        rows: result.rows,
        total: result.total,
        page,
        uploaderId: uploaderId ?? '',
        bucket: bucket ?? '',
        status: status ?? '',
        since: sinceStr ?? '',
        prevUrl,
        nextUrl,
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/files/:id', async (req, res, next) => {
    try {
      const file = await repo.findById(req.params['id']!);
      if (!file) {
        res.status(404).render('error', { message: 'File not found.' });
        return;
      }
      const deliveries = await repo.findDeliveries(req.params['id']!);
      res.render('fileDetail', { file, deliveries });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
```

- [ ] **Step 4: Create `src/app.ts`**

```typescript
import express from 'express';
import path from 'path';
import { auth } from 'express-openid-connect';
import { FileQueryRepository } from './repository/types';
import { fileSearchRouter } from './routes/fileSearch';

export interface OidcConfig {
  issuerBaseURL: string;
  clientID: string;
  clientSecret: string;
  baseURL: string;
}

export function createApp(repo: FileQueryRepository, oidcConfig?: OidcConfig): express.Application {
  const app = express();
  app.set('view engine', 'ejs');
  app.set('views', path.join(__dirname, 'views'));

  if (oidcConfig) {
    app.use(auth({
      issuerBaseURL: oidcConfig.issuerBaseURL,
      clientID: oidcConfig.clientID,
      clientSecret: oidcConfig.clientSecret,
      baseURL: oidcConfig.baseURL,
      secret: oidcConfig.clientSecret,
      authRequired: true,
      auth0Logout: true,
    }));
  }

  app.use('/', fileSearchRouter(repo));

  app.use((_err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    res.status(500).render('error', { message: 'An unexpected error occurred. Please try again.' });
  });

  return app;
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd status-gui-ts && npx jest test/routes/fileSearch
```

Expected: `Tests: 7 passed`

- [ ] **Step 6: Commit**

```bash
git add status-gui-ts/src/app.ts status-gui-ts/src/routes/fileSearch.ts \
        status-gui-ts/test/routes/fileSearch.test.ts
git commit -m "feat(status-gui-ts): add app factory and file search routes with tests"
```

---

## Task 6: Missing Files Routes + Tests

**Files:**
- Create: `status-gui-ts/src/routes/missing.ts`
- Modify: `status-gui-ts/src/app.ts` — register missingRouter
- Test: `status-gui-ts/test/routes/missing.test.ts`

- [ ] **Step 1: Write the failing route tests**

Create `test/routes/missing.test.ts`:

```typescript
import request from 'supertest';
import { createApp } from '../../src/app';
import { FakeFileQueryRepository, sampleFileRow } from '../FakeFileQueryRepository';

describe('GET /missing', () => {
  it('shows prompt when consumerId is absent', async () => {
    const res = await request(createApp(new FakeFileQueryRepository())).get('/missing');
    expect(res.status).toBe(200);
    expect(res.text).toContain('Consumer ID');
    expect(res.text).toContain('Missing Files');
  });

  it('shows results when consumerId is provided', async () => {
    const repo = new FakeFileQueryRepository();
    repo.findMissingResult = { rows: [sampleFileRow({ filename: 'missing_report.csv' })], total: 1 };
    const res = await request(createApp(repo)).get('/missing?consumerId=consumer-a');
    expect(res.status).toBe(200);
    expect(res.text).toContain('missing_report.csv');
  });

  it('shows no results message when consumer has received all files', async () => {
    const repo = new FakeFileQueryRepository();
    repo.findMissingResult = { rows: [], total: 0 };
    const res = await request(createApp(repo)).get('/missing?consumerId=consumer-a');
    expect(res.status).toBe(200);
    expect(res.text).toContain('No missing files');
  });

  it('shows pagination when results exceed page size', async () => {
    const repo = new FakeFileQueryRepository();
    repo.findMissingResult = { rows: [sampleFileRow()], total: 50 };
    const res = await request(createApp(repo)).get('/missing?consumerId=consumer-a');
    expect(res.text).toContain('Next');
    expect(res.text).toContain('50');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd status-gui-ts && npx jest test/routes/missing 2>&1 | tail -5
```

Expected: test failures — `/missing` route returns 404 because `missingRouter` is not registered.

- [ ] **Step 3: Create `src/routes/missing.ts`**

```typescript
import { Router } from 'express';
import { FileQueryRepository } from '../repository/types';

function buildMissingUrl(params: {
  consumerId?: string; bucket?: string; since?: string; page?: number;
}): string {
  const parts: string[] = [];
  if (params.consumerId) parts.push(`consumerId=${encodeURIComponent(params.consumerId)}`);
  if (params.bucket) parts.push(`bucket=${encodeURIComponent(params.bucket)}`);
  if (params.since) parts.push(`since=${params.since}`);
  if (params.page !== undefined && params.page > 0) parts.push(`page=${params.page}`);
  return '/missing' + (parts.length ? '?' + parts.join('&') : '');
}

export function missingRouter(repo: FileQueryRepository): Router {
  const router = Router();

  router.get('/missing', async (req, res, next) => {
    try {
      const consumerId = ((req.query['consumerId'] as string) ?? '').trim() || undefined;
      const bucket = ((req.query['bucket'] as string) ?? '').trim() || undefined;
      const sinceStr = ((req.query['since'] as string) ?? '').trim() || undefined;
      const page = Math.max(0, parseInt((req.query['page'] as string) ?? '0', 10) || 0);

      if (!consumerId) {
        res.render('missing', {
          rows: [], total: 0, page: 0,
          consumerId: '', bucket: '', since: '',
          searched: false, prevUrl: null, nextUrl: null,
        });
        return;
      }

      const since = sinceStr ? new Date(sinceStr) : new Date(Date.now() - 86400 * 1000);
      const result = await repo.findMissing({ consumerId, bucket, since, page });
      const prevUrl = page > 0
        ? buildMissingUrl({ consumerId, bucket, since: sinceStr, page: page - 1 })
        : null;
      const nextUrl = (page + 1) * 20 < result.total
        ? buildMissingUrl({ consumerId, bucket, since: sinceStr, page: page + 1 })
        : null;

      res.render('missing', {
        rows: result.rows,
        total: result.total,
        page,
        consumerId,
        bucket: bucket ?? '',
        since: sinceStr ?? '',
        searched: true,
        prevUrl,
        nextUrl,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
```

- [ ] **Step 4: Register `missingRouter` in `src/app.ts`**

Add the import and registration. The full updated `app.ts`:

```typescript
import express from 'express';
import path from 'path';
import { auth } from 'express-openid-connect';
import { FileQueryRepository } from './repository/types';
import { fileSearchRouter } from './routes/fileSearch';
import { missingRouter } from './routes/missing';

export interface OidcConfig {
  issuerBaseURL: string;
  clientID: string;
  clientSecret: string;
  baseURL: string;
}

export function createApp(repo: FileQueryRepository, oidcConfig?: OidcConfig): express.Application {
  const app = express();
  app.set('view engine', 'ejs');
  app.set('views', path.join(__dirname, 'views'));

  if (oidcConfig) {
    app.use(auth({
      issuerBaseURL: oidcConfig.issuerBaseURL,
      clientID: oidcConfig.clientID,
      clientSecret: oidcConfig.clientSecret,
      baseURL: oidcConfig.baseURL,
      secret: oidcConfig.clientSecret,
      authRequired: true,
      auth0Logout: true,
    }));
  }

  app.use('/', fileSearchRouter(repo));
  app.use('/', missingRouter(repo));

  app.use((_err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    res.status(500).render('error', { message: 'An unexpected error occurred. Please try again.' });
  });

  return app;
}
```

- [ ] **Step 5: Run all route tests**

```bash
cd status-gui-ts && npx jest test/routes
```

Expected: `Tests: 11 passed` (7 fileSearch + 4 missing)

- [ ] **Step 6: Commit**

```bash
git add status-gui-ts/src/routes/missing.ts status-gui-ts/src/app.ts \
        status-gui-ts/test/routes/missing.test.ts
git commit -m "feat(status-gui-ts): add missing files routes with tests"
```

---

## Task 7: Wire index.ts and Verify Full Build

**Files:**
- Modify: `status-gui-ts/src/index.ts` (replace placeholder)

- [ ] **Step 1: Replace `src/index.ts` with the complete entry point**

```typescript
import { createApp } from './app';
import { pool } from './db';
import { MysqlFileQueryRepository } from './repository/MysqlFileQueryRepository';
import { config } from './config';

const repo = new MysqlFileQueryRepository(pool);
const oidcConfig = config.oidc.enabled ? config.oidc : undefined;
const app = createApp(repo, oidcConfig);

app.listen(config.port, () => {
  console.log(`status-gui-ts listening on http://localhost:${config.port}`);
});
```

- [ ] **Step 2: Run all tests**

```bash
cd status-gui-ts && npm test
```

Expected: `Tests: 19 passed` (8 repository + 11 routes), all passing.

- [ ] **Step 3: Build the production bundle**

```bash
cd status-gui-ts && npm run build
```

Expected: `dist/` directory created, `dist/views/` copied in, no TypeScript errors.

Verify:

```bash
ls status-gui-ts/dist/
ls status-gui-ts/dist/views/
```

Expected: `dist/index.js`, `dist/views/_head.ejs`, `dist/views/fileSearch.ejs`, etc.

- [ ] **Step 4: Check root .gitignore covers status-gui-ts/node_modules and dist**

```bash
grep -E "node_modules|dist" /home/brandy/projects/file-exchange-hub/.gitignore
```

If `node_modules` or `dist` are not covered, add to `.gitignore`:

```
status-gui-ts/node_modules/
status-gui-ts/dist/
```

- [ ] **Step 5: Final commit**

```bash
git add status-gui-ts/src/index.ts .gitignore
git commit -m "feat(status-gui-ts): wire index.ts — Express app complete and all tests passing"
```

---

## Running the App

### Prerequisites

Same `gui_reader` MariaDB user as the Kotlin version:

```sql
CREATE USER 'gui_reader'@'%' IDENTIFIED BY '<password>';
GRANT SELECT ON <db>.file_metadata TO 'gui_reader'@'%';
GRANT SELECT ON <db>.file_delivery TO 'gui_reader'@'%';
```

### Start

```bash
cd status-gui-ts
DB_HOST=localhost DB_PORT=3306 DB_NAME=<db> DB_USER=gui_reader DB_PASS=<password> \
  node dist/index.js
```

Open http://localhost:8091 → redirects to File Search.

### With OIDC

```bash
cd status-gui-ts
DB_HOST=localhost DB_PORT=3306 DB_NAME=<db> DB_USER=gui_reader DB_PASS=<password> \
  OIDC_ISSUER_BASE_URL=https://your-idp.example.com \
  OIDC_CLIENT_ID=<client-id> \
  OIDC_CLIENT_SECRET=<secret> \
  OIDC_BASE_URL=http://localhost:8091 \
  node dist/index.js
```
