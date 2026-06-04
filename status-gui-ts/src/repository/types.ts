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
