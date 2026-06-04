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
