import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export class CognitoStack extends Construct {
  public readonly userPool: cognito.UserPool;
  public readonly userPoolClient: cognito.UserPoolClient;
  public readonly identityPool: cognito.CfnIdentityPool;
  public readonly authenticatedRole: iam.Role;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    // User Pool
    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: 'ServerWatcherUserPool',
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      autoVerify: { email: true },
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { sms: false, otp: true },
      passwordPolicy: {
        minLength: 8,
        requireLowercase: true,
        requireUppercase: true,
        requireDigits: true,
        requireSymbols: false,
      },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // User Pool Client
    this.userPoolClient = new cognito.UserPoolClient(this, 'UserPoolClient', {
      userPool: this.userPool,
      userPoolClientName: 'ServerWatcherAndroidClient',
      authFlows: {
        userPassword: true,
        userSrp: true,
      },
      generateSecret: false,
      // The app signs in through the Cognito API directly. Without this, CDK enables the hosted-UI
      // OAuth flows with an example.com callback URL, which nothing here needs.
      disableOAuth: true,
      accessTokenValidity: cdk.Duration.hours(1),
      idTokenValidity: cdk.Duration.hours(1),
      refreshTokenValidity: cdk.Duration.days(30),
    });

    // Identity Pool
    this.identityPool = new cognito.CfnIdentityPool(this, 'IdentityPool', {
      identityPoolName: 'ServerWatcherIdentityPool',
      allowUnauthenticatedIdentities: false,
      cognitoIdentityProviders: [
        {
          clientId: this.userPoolClient.userPoolClientId,
          providerName: this.userPool.userPoolProviderName,
          serverSideTokenCheck: false,
        },
      ],
    });

    // IAM Role for authenticated users
    this.authenticatedRole = new iam.Role(this, 'CognitoAuthRole', {
      assumedBy: new iam.FederatedPrincipal(
        'cognito-identity.amazonaws.com',
        {
          StringEquals: {
            'cognito-identity.amazonaws.com:aud': this.identityPool.ref,
          },
          'ForAnyValue:StringLike': {
            'cognito-identity.amazonaws.com:amr': 'authenticated',
          },
        },
        'sts:AssumeRoleWithWebIdentity',
      ),
    });

    // EC2 mutations — only on instances tagged ManagedBy=ServerWatcher.
    // ModifyInstanceAttribute is used to push env config as user data.
    this.authenticatedRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'ec2:StartInstances',
          'ec2:StopInstances',
          'ec2:ModifyInstanceAttribute',
        ],
        resources: [`arn:${cdk.Aws.PARTITION}:ec2:*:${cdk.Aws.ACCOUNT_ID}:instance/*`],
        conditions: {
          StringEquals: {
            'ec2:ResourceTag/ManagedBy': 'ServerWatcher',
          },
        },
      }),
    );

    // EC2 describe calls do not support resource-level permissions, so they need '*'.
    this.authenticatedRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['ec2:DescribeInstances', 'ec2:DescribeInstanceStatus'],
        resources: ['*'],
      }),
    );

    // Route 53 — list zones (no resource-level support) and upsert A records.
    // Scope record changes to specific zones with:  cdk deploy -c hostedZoneIds=Z123,Z456
    const hostedZoneIds = String(this.node.tryGetContext('hostedZoneIds') ?? '*')
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean);

    this.authenticatedRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['route53:ListHostedZones'],
        resources: ['*'],
      }),
    );

    this.authenticatedRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['route53:ChangeResourceRecordSets'],
        resources: hostedZoneIds.map((id) => `arn:${cdk.Aws.PARTITION}:route53:::hostedzone/${id}`),
      }),
    );

    // Attach role to identity pool
    new cognito.CfnIdentityPoolRoleAttachment(this, 'IdentityPoolRoleAttachment', {
      identityPoolId: this.identityPool.ref,
      roles: {
        authenticated: this.authenticatedRole.roleArn,
      },
    });

    // Outputs
    new cdk.CfnOutput(this, 'UserPoolId', {
      value: this.userPool.userPoolId,
      exportName: 'ServerWatcher-UserPoolId',
    });

    new cdk.CfnOutput(this, 'UserPoolClientId', {
      value: this.userPoolClient.userPoolClientId,
      exportName: 'ServerWatcher-UserPoolClientId',
    });

    new cdk.CfnOutput(this, 'IdentityPoolId', {
      value: this.identityPool.ref,
      exportName: 'ServerWatcher-IdentityPoolId',
    });

    new cdk.CfnOutput(this, 'Region', {
      value: cdk.Stack.of(this).region,
      exportName: 'ServerWatcher-Region',
    });
  }
}
