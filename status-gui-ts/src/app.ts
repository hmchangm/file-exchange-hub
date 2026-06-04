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
