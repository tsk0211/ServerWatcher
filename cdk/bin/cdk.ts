#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { ServerWatcherStack } from '../lib';

const app = new cdk.App();
new ServerWatcherStack(app, 'ServerWatcherStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
  },
  description: 'ServerWatcher Cognito Auth Stack',
});
