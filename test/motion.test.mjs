import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const script = readFileSync(new URL('../docs/site/script.js', import.meta.url), 'utf8');
function element() {
  const classes = new Set();
  return { classList: { add: name => classes.add(name), contains: name => classes.has(name) } };
}
function fixture({ reduced = false, observersAvailable = true } = {}) {
  const words = Array.from({ length: 4 }, element);
  const sections = Array.from({ length: 3 }, element);
  const root = element();
  const observers = [];
  const frames = [];
  const listeners = new Map();
  let top = 900;
  const tagline = { getBoundingClientRect: () => ({ top }) };
  const media = { matches: reduced, addEventListener: () => {} };
  const window = {
    innerHeight: 1000,
    matchMedia: query => query.includes('reduced') ? media : { matches: false },
    addEventListener: (type, fn) => listeners.set(type, fn),
    removeEventListener: type => listeners.delete(type),
  };
  class Observer {
    constructor(callback) { this.callback = callback; this.removed = []; observers.push(this); }
    observe() {}
    unobserve(target) { this.removed.push(target); }
  }
  if (observersAvailable) window.IntersectionObserver = Observer;
  const document = {
    documentElement: root,
    getElementById: id => id === 'tagline-heading' ? tagline : null,
    querySelector: () => null,
    querySelectorAll: selector => selector === '.reveal' ? sections : selector === '.tagline-word' ? words : [],
  };
  vm.runInNewContext(script, { document, window, IntersectionObserver: Observer, requestAnimationFrame: fn => frames.push(fn) });
  return { words, sections, root, observers, frames, listeners, setTop: value => { top = value; }, flush: () => { while (frames.length) frames.shift()(); } };
}

test('reduced motion shows every tagline word in its final state immediately', () => {
  const f = fixture({ reduced: true });
  assert.ok(f.words.every(word => word.classList.contains('is-active')));
  assert.ok(!f.listeners.has('scroll'));
});

test('browsers without observers keep sections readable and the enhanced menu available', () => {
  const f = fixture({ observersAvailable: false });
  assert.ok(f.sections.every(section => section.classList.contains('is-visible')));
  assert.ok(f.words.every(word => word.classList.contains('is-active')));
  assert.ok(f.root.classList.contains('js'));
});

test('scroll updates coalesce into one frame and activate words in reading order', () => {
  const f = fixture();
  f.setTop(795);
  f.observers[2].callback([{ isIntersecting: true }]);
  f.listeners.get('scroll')();
  f.listeners.get('scroll')();
  assert.equal(f.frames.length, 1);
  f.flush();
  assert.deepEqual(f.words.map(word => word.classList.contains('is-active')), [true, false, false, false]);
  f.setTop(700);
  f.listeners.get('scroll')();
  f.flush();
  assert.deepEqual(f.words.map(word => word.classList.contains('is-active')), [true, true, false, false]);
  f.setTop(500);
  f.listeners.get('scroll')();
  f.flush();
  assert.ok(f.words.every(word => word.classList.contains('is-active')));
  assert.ok(!f.listeners.has('scroll'), 'stop work after the final reveal');
});

test('section reveals are observed once, not replayed on every scroll', () => {
  const f = fixture();
  f.observers[0].callback([{ isIntersecting: true, target: f.sections[0] }]);
  assert.ok(f.sections[0].classList.contains('is-visible'));
  assert.deepEqual(f.observers[0].removed, [f.sections[0]]);
});
