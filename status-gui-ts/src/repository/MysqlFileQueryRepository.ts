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
