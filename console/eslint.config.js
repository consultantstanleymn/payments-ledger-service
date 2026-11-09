import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';

export default tseslint.config(
  {
    ignores: ['node_modules/**', 'dist/**', 'test-results/**', 'playwright-report/**']
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'jsx-a11y/anchor-is-valid': 'error',
      'jsx-a11y/no-autofocus': 'warn',
      'jsx-a11y/alt-text': 'error'
    }
  }
);

