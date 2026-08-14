const { spawn } = require('child_process');

const secretName = 'PAYSTACK_SECRET_KEY';
const secretValue = 'sk_live_fdd328a31cc489d549a6a0e604f167c36854a116';
const projectName = 'securepay-dashboard';

console.log(`Setting secret ${secretName} on Pages project ${projectName}...`);

const proc = spawn('npx', ['wrangler', 'pages', 'secret', 'put', secretName, '--project-name', projectName], {
  shell: true,
  stdio: ['pipe', 'inherit', 'inherit']
});

proc.stdin.write(secretValue + '\n');
proc.stdin.end();

proc.on('close', (code) => {
  console.log(`Wrangler process exited with code ${code}`);
  process.exit(code || 0);
});
