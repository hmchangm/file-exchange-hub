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
