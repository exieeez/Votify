/**
 * animation.js — Animation utilities
 * Stagger, spring, scroll-reveal, FLIP
 */

// Staggered animations
export function stagger(elements, animation, options = {}) {
  const {
    delay = 40,
    start = 0,
    easing = 'cubic-bezier(0.16, 1, 0.3, 1)',
    duration = 320,
    from = { opacity: 0, transform: 'translateY(20px)' },
    to = { opacity: 1, transform: 'translateY(0)' },
  } = options;

  elements.forEach((el, i) => {
    const elementDelay = start + i * delay;
    el.style.animation = `${animation} ${duration}ms ${easing} ${elementDelay}ms both`;
    // Fallback for non-keyframe animations
    if (!animation.includes('@keyframes')) {
      Object.assign(el.style, from);
      setTimeout(() => {
        el.style.transition = `all ${duration}ms ${easing}`;
        Object.assign(el.style, to);
      }, elementDelay);
    }
  });
}

// Spring animation
export function spring(el, props, options = {}) {
  const { stiffness = 300, damping = 30, mass = 1, velocity = 0, onUpdate, onComplete } = options;

  let frameId;
  let currentVelocity = velocity;
  const startTime = performance.now();
  const initialProps = {};

  Object.keys(props).forEach(key => {
    initialProps[key] = parseFloat(getComputedStyle(el)[key]) || 0;
  });

  function animate(time) {
    const elapsed = (time - startTime) / 1000;
    let done = true;

    Object.keys(props).forEach(key => {
      const target = props[key];
      const initial = initialProps[key];
      const displacement = target - initial;

      // Spring physics
      const springForce = -stiffness * displacement;
      const dampingForce = -damping * currentVelocity;
      const acceleration = (springForce + dampingForce) / mass;

      currentVelocity += acceleration * 0.016;
      const current = initial + displacement + currentVelocity * elapsed;

      el.style[key] =
        current +
        (key.includes('translate') || key.includes('width') || key.includes('height') ? 'px' : '');

      if (Math.abs(currentVelocity) > 0.1 || Math.abs(displacement) > 0.1) done = false;
    });

    onUpdate?.(el);

    if (!done) {
      frameId = requestAnimationFrame(animate);
    } else {
      onComplete?.(el);
    }
  }

  frameId = requestAnimationFrame(animate);

  return () => cancelAnimationFrame(frameId);
}

// Scroll reveal with IntersectionObserver
export function createScrollReveal(options = {}) {
  const {
    root = null,
    rootMargin = '0px 0px -50px 0px',
    threshold = 0.1,
    once = true,
    onReveal,
  } = options;

  const observer = new IntersectionObserver(
    entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
          onReveal?.(entry.target);
          if (once) observer.unobserve(entry.target);
        } else if (!once) {
          entry.target.classList.remove('in-view');
        }
      });
    },
    { root, rootMargin, threshold }
  );

  return {
    observe(el) {
      observer.observe(el);
    },
    unobserve(el) {
      observer.unobserve(el);
    },
    disconnect() {
      observer.disconnect();
    },
  };
}

// FLIP animation (First, Last, Invert, Play)
export function flip(element, callback) {
  const first = element.getBoundingClientRect();
  const result = callback();
  const last = element.getBoundingClientRect();

  const dx = first.left - last.left;
  const dy = first.top - last.top;
  const dw = first.width / last.width;
  const dh = first.height / last.height;

  element.style.transform = `translate(${dx}px, ${dy}px) scale(${dw}, ${dh})`;
  element.style.transition = 'transform 0ms';

  // Force reflow
  element.offsetHeight;

  element.style.transition = 'transform 300ms cubic-bezier(0.16, 1, 0.3, 1)';
  element.style.transform = '';

  return new Promise(resolve => {
    const onEnd = e => {
      if (e.propertyName === 'transform') {
        element.removeEventListener('transitionend', onEnd);
        element.style.transition = '';
        resolve();
      }
    };
    element.addEventListener('transitionend', onEnd);
  });
}

// Keyframe helpers
export const keyframes = {
  fadeIn: [{ opacity: 0 }, { opacity: 1 }],
  fadeOut: [{ opacity: 1 }, { opacity: 0 }],
  slideUp: [
    { opacity: 0, transform: 'translateY(20px)' },
    { opacity: 1, transform: 'translateY(0)' },
  ],
  slideDown: [
    { opacity: 0, transform: 'translateY(-20px)' },
    { opacity: 1, transform: 'translateY(0)' },
  ],
  scaleIn: [
    { opacity: 0, transform: 'scale(0.95)' },
    { opacity: 1, transform: 'scale(1)' },
  ],
  rotateIn: [
    { opacity: 0, transform: 'rotate(-10deg) scale(0.9)' },
    { opacity: 1, transform: 'rotate(0) scale(1)' },
  ],
};

// Web Animations API wrapper
export function animate(el, keyframes, options = {}) {
  const {
    duration = 300,
    easing = 'cubic-bezier(0.16, 1, 0.3, 1)',
    fill = 'both',
    delay = 0,
    ...rest
  } = options;

  return el.animate(keyframes, { duration, easing, fill, delay, ...rest });
}

// Promise wrapper for animation
export function animateAsync(el, keyframes, options = {}) {
  const animation = animate(el, keyframes, options);
  return animation.finished;
}

// Sequence multiple animations
export async function sequence(animations) {
  for (const anim of animations) {
    await anim;
  }
}

// Parallel animations
export function parallel(animations) {
  return Promise.all(animations.map(a => a.finished || a));
}

// CSS animation trigger
export function triggerCssAnimation(el, className, duration = 300) {
  return new Promise(resolve => {
    const onEnd = e => {
      if (e.target === el && e.animationName !== '') {
        el.removeEventListener('animationend', onEnd);
        el.classList.remove(className);
        resolve();
      }
    };
    el.addEventListener('animationend', onEnd);
    el.classList.add(className);
  });
}

// Reduced motion check
export const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default {
  stagger,
  spring,
  createScrollReveal,
  flip,
  keyframes,
  animate,
  animateAsync,
  sequence,
  parallel,
  triggerCssAnimation,
  prefersReducedMotion,
};
