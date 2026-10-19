import js from '@eslint/js'
import { defineConfig } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'

export default defineConfig(
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      'id-length': [
        'warn',
        {
          min: 2,
          exceptions: ['i', 'j', 'k', 'x', 'y', 'w', 'h', '_', 'e'],
          properties: 'never',
        },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      // Editor passes reactive screen/tile models as props and mutates them in place.
      'vue/no-mutating-props': 'off',
      'vue/no-side-effects-in-computed-properties': 'warn',
      'vue/html-self-closing': [
        'warn',
        { html: { void: 'always', normal: 'always', component: 'always' } },
      ],
    },
  },
  prettier,
)
