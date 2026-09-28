const js = require("@eslint/js");
const tsPlugin = require("@typescript-eslint/eslint-plugin");
const tsParser = require("@typescript-eslint/parser");
const react = require("eslint-plugin-react");
const prettier = require("eslint-plugin-prettier");

module.exports = [
  js.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
      globals: {
        Atomics: "readonly",
        SharedArrayBuffer: "readonly",
      },
    },
    plugins: {
      "@typescript-eslint": tsPlugin,
      react,
      prettier,
    },
    settings: {
      react: {
        // eslint-plugin-react's "detect" calls context.getFilename(), which
        // ESLint 10 removed. Keep this in sync with the react dependency.
        version: "19.3",
      },
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      ...react.configs.recommended.rules,
      // TypeScript's own compiler already catches undefined references.
      "no-undef": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": [
        "error",
        { args: "none", caughtErrors: "none" },
      ],
      "prettier/prettier": "error",
    },
  },
];
