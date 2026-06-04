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
