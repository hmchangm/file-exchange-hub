import { Router } from 'express';
import { FileQueryRepository } from '../repository/types';

function buildMissingUrl(params: {
  consumerId?: string; bucket?: string; since?: string; page?: number;
}): string {
  const parts: string[] = [];
  if (params.consumerId) parts.push(`consumerId=${encodeURIComponent(params.consumerId)}`);
  if (params.bucket) parts.push(`bucket=${encodeURIComponent(params.bucket)}`);
  if (params.since) parts.push(`since=${encodeURIComponent(params.since)}`);
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
