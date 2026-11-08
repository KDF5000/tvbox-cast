'use strict';
const toggle = document.querySelector('.menu-toggle');
const navigation = document.getElementById('main-navigation');
const main = document.querySelector('main');
const footer = document.querySelector('footer');
const header = document.querySelector('.nav');
const mobile = window.matchMedia('(max-width: 720px)');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

if (toggle && navigation) {
  function setMenu(open, returnFocus = false) {
    const expanded = open && mobile.matches;
    toggle.setAttribute('aria-expanded', String(expanded));
    toggle.setAttribute('aria-label', expanded ? '关闭导航菜单' : '打开导航菜单');
    navigation.classList.toggle('is-open', expanded);
    document.body.classList.toggle('menu-open', expanded);
    navigation.inert = mobile.matches && !expanded;
    main.inert = expanded;
    footer.inert = expanded;
    if (returnFocus) toggle.focus();
  }
  toggle.addEventListener('click', () => setMenu(toggle.getAttribute('aria-expanded') !== 'true'));
  navigation.addEventListener('click', event => {
    if (event.target.closest('a')) setMenu(false);
  });
  document.addEventListener('keydown', event => {
    if (toggle.getAttribute('aria-expanded') !== 'true') return;
    if (event.key === 'Escape') {
      event.preventDefault();
      setMenu(false, true);
    } else if (event.key === 'Tab') {
      const items = [...header.querySelectorAll('a, button')];
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  });
  mobile.addEventListener('change', () => setMenu(false));
  setMenu(false);
}

// Leave the complete page readable when JavaScript or observers are unavailable.
if ('IntersectionObserver' in window) {
  const revealObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-visible');
      revealObserver.unobserve(entry.target);
    });
  }, { threshold: 0.08 });
  document.querySelectorAll('.reveal').forEach(section => revealObserver.observe(section));

  const sectionLinks = [...document.querySelectorAll('.nav-links a[href^="#"]')];
  const currentObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      sectionLinks.forEach(link => {
        if (link.hash === `#${entry.target.id}`) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      });
    });
  }, { rootMargin: '-15% 0px -60% 0px', threshold: 0 });
  sectionLinks.forEach(link => {
    const section = document.querySelector(link.hash);
    if (section) currentObserver.observe(section);
  });

  const tagline = document.getElementById('tagline-heading');
  const words = [...document.querySelectorAll('.tagline-word')];
  let queued = false;
  let watching = false;
  let complete = false;
  function updateWords() {
    queued = false;
    if (!tagline || complete) return;
    const top = tagline.getBoundingClientRect().top;
    const progress = (window.innerHeight * .8 - top) / (window.innerHeight * .3);
    // A short page may end before the tagline reaches the normal trigger line.
    const atBottom = window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
    const count = reducedMotion.matches || (atBottom && watching && top < window.innerHeight)
      ? words.length : Math.max(0, Math.min(words.length, Math.ceil(progress * words.length)));
    words.forEach((word, index) => {
      if (index < count) word.classList.add('is-active');
    });
    if (count === words.length) {
      complete = true;
      window.removeEventListener('scroll', queueWords);
    }
  }
  function queueWords() {
    if (!watching || queued || complete) return;
    queued = true;
    requestAnimationFrame(updateWords);
  }
  const taglineObserver = new IntersectionObserver(entries => {
    watching = entries[0].isIntersecting;
    if (watching) queueWords();
  });
  if (tagline) {
    taglineObserver.observe(tagline);
    window.addEventListener('scroll', queueWords, { passive: true });
    window.addEventListener('resize', queueWords);
    reducedMotion.addEventListener('change', () => {
      if (reducedMotion.matches) updateWords();
    });
    if (reducedMotion.matches) updateWords();
  }
  document.documentElement.classList.add('js');
} else {
  document.querySelectorAll('.reveal').forEach(section => section.classList.add('is-visible'));
  document.querySelectorAll('.tagline-word').forEach(word => word.classList.add('is-active'));
  document.documentElement.classList.add('js');
}
