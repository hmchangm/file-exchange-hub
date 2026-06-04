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
