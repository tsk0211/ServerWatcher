import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { CognitoStack } from './CognitoStack';

export class ServerWatcherStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);
    new CognitoStack(this, 'Cognito');
  }
}
