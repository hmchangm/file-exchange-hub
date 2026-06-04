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
