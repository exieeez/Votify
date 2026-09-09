const fs = require('fs');
const path = require('path');

const output = path.join(__dirname, '..', 'firebase-config.json');
if (process.env.VOTIFY_FIREBASE_CONFIG) {
  const config = JSON.parse(process.env.VOTIFY_FIREBASE_CONFIG);
  if (config.private_key || config.type === 'service_account') {
    throw new Error('VOTIFY_FIREBASE_CONFIG must contain Web Config, not a Service Account');
  }
  fs.writeFileSync(output, JSON.stringify(config, null, 2));
  console.log('[firebase] Web Config prepared for packaging');
} else if (!fs.existsSync(output)) {
  console.warn(
    '[firebase] firebase-config.json is missing; the packaged app will run without cloud accounts'
  );
}
