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
const checksum = '569af6860bde9be0895954259af6c998903aa0639ebf2246bd9d75f382d45aa2';

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

test('one consistent APK action appears at hero, download and final CTA', () => {
  const buttons = [...html.matchAll(/<a class="button primary button-md"[^>]*href="([^"]+)"[^>]*>([^<]+)/g)];
  assert.equal(buttons.length, 3);
  buttons.forEach(([, url, label]) => {
    assert.equal(url, apk);
    assert.equal(label.trim(), '下载 Android 版');
  });
  assert.ok(html.includes(checksum));
});

test('compatibility, network risk, signature and private-source boundaries remain explicit', () => {
  for (const fact of ['152 项', 'EC6108V9_8G', '全面兼容', '标准 DLNA', '没有 PIN', 'MIT 许可证', '不能直接覆盖', 'AirPlay', 'Google Cast', 'DRM', '可信家庭网络']) assert.ok(html.includes(fact), fact);
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

test('six directly readable questions and a separate two-line tagline', () => {
  assert.equal([...html.matchAll(/class="question reveal"/g)].length, 6);
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

test('menu and copy ship keyboard, focus, loading and error handling', () => {
  assert.match(js, /event.key === 'Escape'/);
  assert.match(js, /event.key === 'Tab'/);
  assert.match(js, /navigation.inert/);
  assert.match(css, /:focus-visible/);
  assert.match(js, /aria-busy/);
  assert.match(js, /自动复制失败/);
  assert.match(html, /aria-live="polite"/);
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
