import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../docs/site');
const html = readFileSync(resolve(root, 'index.html'), 'utf8');
const css = readFileSync(resolve(root, 'style.css'), 'utf8');
const js = readFileSync(resolve(root, 'script.js'), 'utf8');
const apk = 'https://github.com/KDF5000/tvbox-cast/releases/download/v2.0.1-beta.1/tvbox-cast-2.0.1-beta.1.apk';

for (const file of ['index.html', 'terms.html', '404.html']) {
  const content = readFileSync(resolve(root, file), 'utf8');
  test(`${file}: local assets, anchors and legal navigation resolve`, () => {
    const ids = [...content.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
    assert.equal(ids.length, new Set(ids).size, 'duplicate IDs');
    for (const [, target] of content.matchAll(/(?:href|src)="([^"]+)"/g)) {
      assert.notEqual(target, '#', 'dead link');
      if (target.startsWith('#')) assert.ok(ids.includes(target.slice(1)), `missing ${target}`);
      else if (!target.startsWith('https:')) {
        const local = target === '/' ? 'index.html' : target.replace(/^\//, '');
        assert.ok(existsSync(resolve(root, local)), `missing ${local}`);
      }
    }
    assert.match(content, /class="skip/);
    assert.match(content, /<main/);
    assert.equal([...content.matchAll(/<h1\b/g)].length, 1);
    for (const image of content.matchAll(/<img\b[^>]*>/g)) assert.match(image[0], /\balt="/);
  });
}

test('one consistent APK action appears only at hero and final download', () => {
  const buttons = [...html.matchAll(/<a class="button primary button-md"[^>]*href="([^"]+)"[^>]*>([^<]+)/g)];
  assert.equal(buttons.length, 2);
  buttons.forEach(([, url, label]) => {
    assert.equal(url, apk);
    assert.equal(label.trim(), '下载 Android 版');
  });
});

test('brief compatibility and beta notices remain, with detailed docs one click away', () => {
  for (const fact of ['Android 4.4', '标准 DLNA', '可信家庭网络', '测试版', 'MIT 开源']) assert.ok(html.includes(fact), fact);
  for (const doc of ['docs/INSTALL.md', 'docs/FAQ.md', 'docs/PRIVACY.md', 'releases/tag/v2.0.1-beta.1']) assert.ok(html.includes(doc), doc);
});

test('homepage stays a short download page rather than a manual', () => {
  const body = html.split('<body>')[1].replace(/<[^>]+>/g, '');
  assert.ok((body.match(/[\u4e00-\u9fff]/g) || []).length <= 300, 'keep homepage copy concise');
  assert.equal([...html.matchAll(/<section\b/g)].length, 4);
  assert.equal([...html.matchAll(/<li class="reveal"/g)].length, 3);
  assert.doesNotMatch(html, /class="(?:release-card|question|signal-panel)|id="checksum-value"/);
});

test('approved typography and a single heading-only gradient', () => {
  assert.match(css, /font-family:Geist/);
  assert.doesNotMatch(css, /font-family:[^;}]*\b(Inter|Roboto|Arial|Helvetica)\b/);
  assert.doesNotMatch(css, /\.serif\{/);
  const scale = new Set([12, 14, 16, 18, 20, 24, 30, 36, 48, 60, 72, 96, 128]);
  for (const [, size] of css.matchAll(/font-size:(\d+)px/g)) assert.ok(scale.has(Number(size)), size);
  assert.equal([...css.matchAll(/gradient\(/g)].length, 1);
  assert.match(css, /\.hero h1\{[^}]*background-clip:text/);
  assert.doesNotMatch(css, /clamp\(/);
});

test('font files and their licenses are bundled locally', () => {
  for (const file of ['geist.woff2', 'geist-mono.woff2', 'GEIST-LICENSE.txt', 'PHOSPHOR-LICENSE.txt']) assert.ok(existsSync(resolve(root, 'assets', file)));
  assert.doesNotMatch(css, /https?:/);
  assert.doesNotMatch(html, /<script[^>]+src="https?:/);
});

test('final download contains a separate two-line tagline without another repeated section', () => {
  assert.equal([...html.matchAll(/class="tagline-line"/g)].length, 2);
  assert.equal([...html.matchAll(/class="tagline-word"/g)].length, 4);
  assert.doesNotMatch(html, /<details/);
});

test('motion uses observers and frame-throttled word updates, with reduced-motion support', () => {
  assert.match(js, /new IntersectionObserver/);
  assert.match(js, /requestAnimationFrame\(updateWords\)/);
  assert.match(js, /removeEventListener\('scroll', queueWords\)/);
  assert.match(css, /prefers-reduced-motion:reduce/);
  assert.match(css, /cubic-bezier\(\.22,1,\.36,1\)/);
  assert.doesNotMatch(css, /\b(ease-in-out|linear|ease)\s*[,;}]/);
});

test('menu retains keyboard navigation, focus and background isolation', () => {
  assert.match(js, /event.key === 'Escape'/);
  assert.match(js, /event.key === 'Tab'/);
  assert.match(js, /navigation.inert/);
  assert.match(css, /:focus-visible/);
  assert.match(js, /main.inert = expanded/);
  assert.doesNotMatch(js, /copy-checksum|clipboard/);
  assert.doesNotMatch(js, /alert\(/);
});

test('privacy and terms links are present; 404 does not get indexed', () => {
  assert.match(html, /href="\/terms.html"/);
  assert.match(html, /docs\/PRIVACY.md/);
  assert.match(readFileSync(resolve(root, '404.html'), 'utf8'), /name="robots" content="noindex"/);
});

test('open-source links and terms agree with MIT licensing', () => {
  assert.match(html, /tree\/main\/cast-launcher/);
  assert.match(readFileSync(resolve(root, 'terms.html'), 'utf8'), /blob\/main\/LICENSE/);
  assert.doesNotMatch(html, /源码暂未开源|不代表授予再分发或商用授权/);
});
