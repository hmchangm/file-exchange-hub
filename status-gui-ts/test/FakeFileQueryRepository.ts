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

  async search(_params: Parameters<FileQueryRepository['search']>[0]): Promise<PagedResult> {
    return this.searchResult;
  }
  async findById(_id: string): Promise<FileRow | null> { return this.findByIdResult; }
  async findDeliveries(_fileId: string): Promise<DeliveryRow[]> { return this.findDeliveriesResult; }
  async findMissing(_params: Parameters<FileQueryRepository['findMissing']>[0]): Promise<PagedResult> {
    return this.findMissingResult;
  }
}
