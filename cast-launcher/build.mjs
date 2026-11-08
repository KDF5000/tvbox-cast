import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Offline local build only: never installs to a device or reads a release key.
const flags = process.argv.slice(2);
if (flags.includes('--help')) {
  console.log('Usage: node cast-launcher/build.mjs [--unsigned]\nRequires Node 18+, JDK 17, zip, ANDROID_SDK_ROOT, platform android-34 and build-tools 34.0.0.\nDefault: locally signed debug APK. --unsigned: aligned APK without a signature.');
  process.exit(0);
}
if (flags.some(flag => flag !== '--unsigned')) throw new Error('Unknown option. Use --help.');
const here = path.dirname(fileURLToPath(import.meta.url));
const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
if (!sdk) throw new Error('Set ANDROID_SDK_ROOT to your installed Android SDK directory.');
const tools = path.join(sdk, 'build-tools', '34.0.0');
const android = path.join(sdk, 'platforms', 'android-34', 'android.jar');
const javaTool = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name) : name;
for (const file of [android, ...['aapt', 'd8', 'zipalign', 'apksigner'].map(name => path.join(tools, name))]) {
  if (!fs.existsSync(file)) throw new Error(`Missing SDK file: ${file}. Install platforms;android-34 and build-tools;34.0.0.`);
}
const run = (command, args) => {
  const result = spawnSync(command, args, { encoding: 'utf8', env: process.env, cwd: here });
  if (result.error) throw new Error(`Cannot run ${path.basename(command)}: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`${path.basename(command)} failed:\n${result.stderr || result.stdout}`);
  return result.stdout;
};
const manifest = fs.readFileSync(path.join(here, 'AndroidManifest.xml'), 'utf8');
const version = manifest.match(/android:versionName="([a-zA-Z0-9.-]+)"/)?.[1];
if (!version || manifest.includes('SenderService')) throw new Error('Expected the standalone public manifest.');
const buildRoot = path.join(here, 'build');
fs.mkdirSync(buildRoot, { recursive: true });
// New workspace per build prevents stale classes without deleting user files.
const work = fs.mkdtempSync(path.join(buildRoot, 'work-'));
const classes = path.join(work, 'classes');
const generated = path.join(work, 'generated');
const dex = path.join(work, 'dex');
for (const directory of [classes, generated, dex]) fs.mkdirSync(directory);
const unsigned = path.join(work, 'unsigned.apk');
run(path.join(tools, 'aapt'), ['package', '-f', '-m', '-M', path.join(here, 'AndroidManifest.xml'),
  '-S', path.join(here, 'res'), '-J', generated, '-I', android, '-F', unsigned]);
const sourceRoot = path.join(here, 'src', 'local', 'tvbox', 'castlauncher');
const sources = fs.readdirSync(sourceRoot).filter(file => file.endsWith('.java')).sort().map(file => path.join(sourceRoot, file));
run(javaTool('javac'), ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', android,
  '-d', classes, ...sources, path.join(generated, 'local', 'tvbox', 'castlauncher', 'R.java')]);
const jar = path.join(work, 'classes.jar');
run(javaTool('jar'), ['cf', jar, '-C', classes, '.']);
run(path.join(tools, 'd8'), ['--min-api', '19', '--lib', android, '--output', dex, jar]);
run('zip', ['-q', '-j', unsigned, path.join(dex, 'classes.dex')]);
const aligned = path.join(work, 'aligned.apk');
run(path.join(tools, 'zipalign'), ['-f', '4', unsigned, aligned]);
if (flags.includes('--unsigned')) {
  const output = path.join(buildRoot, `tvbox-cast-${version}-unsigned.apk`);
  fs.copyFileSync(aligned, output);
  console.log(output);
} else {
  const key = path.join(buildRoot, 'debug.keystore');
  if (!fs.existsSync(key)) {
    run(javaTool('keytool'), ['-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
      '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
      '-dname', 'CN=TVBox Cast Debug,O=Local Development,C=CN']);
    fs.chmodSync(key, 0o600);
  }
  const output = path.join(buildRoot, `tvbox-cast-${version}-debug.apk`);
  run(path.join(tools, 'apksigner'), ['sign', '--ks', key, '--ks-key-alias', 'androiddebugkey',
    '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', output, aligned]);
  run(path.join(tools, 'apksigner'), ['verify', '--verbose', output]);
  console.log(`${output}\nSigned with a local debug key, not the official release certificate.`);
}
