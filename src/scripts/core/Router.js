/**
 * Router.js — Hash-based SPA Router
 * Simple, zero-deps, supports route guards & params
 */

export class Router {
  constructor(routes, outlet, options = {}) {
    this.routes = routes;
    this.outlet = outlet;
    this.options = {
      defaultRoute: '/',
      notFoundRoute: '/404',
      ...options,
    };
    this.currentRoute = null;
    this.currentParams = {};
    this._guards = new Map();

    this._bindEvents();
    this._handleHashChange();
  }

  _bindEvents() {
    window.addEventListener('hashchange', () => this._handleHashChange());
    window.addEventListener('popstate', () => this._handleHashChange());
  }

  _handleHashChange() {
    const hash = window.location.hash.slice(1) || this.options.defaultRoute;
    this.navigate(hash, { replace: true });
  }

  addGuard(route, guard) {
    this._guards.set(route, guard);
  }

  async navigate(path, options = {}) {
    const { replace = false, state = {} } = options;

    // Parse route and params
    const { route, params } = this._matchRoute(path);
    if (!route) {
      return this.navigate(this.options.notFoundRoute, options);
    }

    // Run guards
    const guard = this._guards.get(route.path);
    if (guard) {
      const canProceed = await guard({ to: route, from: this.currentRoute, params, state });
      if (!canProceed) return false;
    }

    // Update URL
    const newHash = this._buildHash(route.path, params);
    if (replace) {
      history.replaceState(state, '', `#${newHash}`);
    } else {
      history.pushState(state, '', `#${newHash}`);
    }

    // Render
    await this._render(route, params, state);
    this.currentRoute = route;
    this.currentParams = params;

    // Emit event
    import('./EventBus.js').then(({ eventBus, EVENTS }) => {
      eventBus.emit(EVENTS.ROUTE_CHANGE, { route, params, state });
    });

    return true;
  }

  _matchRoute(path) {
    // Split path and query
    const [pathname, search] = path.split('?');
    const params = new URLSearchParams(search);

    // Find matching route
    for (const [pattern, route] of Object.entries(this.routes)) {
      const match = this._matchPattern(pattern, pathname);
      if (match) {
        // Merge URL params with route params
        for (const [k, v] of params) match.params[k] = v;
        return { route: { ...route, path: pattern }, params: match.params };
      }
    }
    return { route: null, params: {} };
  }

  _matchPattern(pattern, pathname) {
    const patternParts = pattern.split('/');
    const pathParts = pathname.split('/');
    const params = {};

    if (patternParts.length !== pathParts.length) return null;

    for (let i = 0; i < patternParts.length; i++) {
      const p = patternParts[i];
      const actual = pathParts[i];

      if (p.startsWith(':')) {
        params[p.slice(1)] = actual;
      } else if (p !== actual) {
        return null;
      }
    }
    return { params };
  }

  _buildHash(pattern, params) {
    let hash = pattern;
    for (const [key, value] of Object.entries(params)) {
      hash = hash.replace(`:${key}`, value);
    }
    return hash;
  }

  async _render(route, params, state) {
    if (!this.outlet) return;

    // Show loading
    this.outlet.innerHTML = '<div class="page-loading">Загрузка...</div>';
    this.outlet.classList.add('page-transition');

    try {
      // Lazy load component if it's a function
      let Component = route.component;
      if (typeof Component === 'function') {
        Component = await Component();
      }
      if (Component?.default) Component = Component.default;

      // Render
      if (typeof Component === 'function') {
        const html = await Component({ params, state, route: this });
        this.outlet.innerHTML = html;
      } else if (typeof Component === 'string') {
        this.outlet.innerHTML = Component;
      }

      // Initialize component scripts if needed
      this._initComponents(this.outlet);
    } catch (e) {
      console.error('Route render error:', e);
      this.outlet.innerHTML = `<div class="page-error">Ошибка загрузки: ${e.message}</div>`;
    }

    this.outlet.classList.remove('page-transition');
    window.scrollTo(0, 0);
  }

  _initComponents(container) {
    // Auto-initialize web components
    container.querySelectorAll('[data-component]').forEach(el => {
      const name = el.dataset.component;
      if (window[name] && typeof window[name].init === 'function') {
        window[name].init(el);
      }
    });
  }

  // Convenience methods
  go(path) {
    return this.navigate(path);
  }
  back() {
    history.back();
  }
  forward() {
    history.forward();
  }
  reload() {
    return this.navigate(this.currentRoute.path, { replace: true });
  }
  getParams() {
    return { ...this.currentParams };
  }
  getRoute() {
    return this.currentRoute;
  }
}

export default Router;
