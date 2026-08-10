module.exports = {
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  extends: ['eslint:recommended', 'plugin:prettier/recommended'],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    'prettier/prettier': 'error',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-console': 'off',
  },
  overrides: [
    {
      files: ['src/main.js'],
      rules: {
        'no-unused-vars': 'off',
      },
    },
  ],
  ignorePatterns: ['dist/', 'node_modules/', '*.min.js'],
};
