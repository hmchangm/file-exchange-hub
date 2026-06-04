import type { Config } from 'jest';
import path from 'path';
import os from 'os';
import fs from 'fs';

// On WSL/Linux the Docker config may reference a Windows credential store
// (e.g. credsStore: "desktop.exe") that is unavailable.  Point DOCKER_CONFIG
// at a minimal config with no credStore so Testcontainers can start containers
// without a credential-provider error.
const tmpDockerConfig = path.join(os.tmpdir(), 'jest-docker-config');
if (!fs.existsSync(tmpDockerConfig)) fs.mkdirSync(tmpDockerConfig, { recursive: true });
const tmpConfigFile = path.join(tmpDockerConfig, 'config.json');
if (!fs.existsSync(tmpConfigFile)) fs.writeFileSync(tmpConfigFile, '{}');
process.env['DOCKER_CONFIG'] = tmpDockerConfig;

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/test'],
  testMatch: ['**/*.test.ts'],
  testTimeout: 5000,
};

export default config;
