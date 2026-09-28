import js from "@eslint/js";
import eslintComments from "@eslint-community/eslint-plugin-eslint-comments";
import boundaries from "eslint-plugin-boundaries";
import importPlugin from "eslint-plugin-import";
import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";

/**
 * Bốn tầng, phụ thuộc chỉ đi một chiều: app → modules → entities → shared.
 *
 * Hai luật quan trọng nhất được máy kiểm tra thay vì trông vào kỷ luật: module không được import
 * module khác (nếu cần dùng chung thì thứ đó thuộc entities/shared), và entity không được import
 * entity khác. Vi phạm hai điều này là cách một codebase dần biến thành một cục.
 */
const LAYERS = {
  elements: [
    { type: "app", pattern: "src/app/**" },
    { type: "modules", pattern: "src/modules/*/**", capture: ["module"] },
    { type: "entities", pattern: "src/entities/*/**", capture: ["entity"] },
    { type: "shared", pattern: "src/shared/**" },
  ],
  policies: [
    {
      from: { element: { type: "app" } },
      allow: { to: { element: { types: { anyOf: ["app", "modules", "entities", "shared"] } } } },
    },
    {
      from: { element: { type: "modules" } },
      allow: [
        // Chỉ chính module đó: "{{from.module}}" là tên module của tệp đang import.
        { to: { element: { type: "modules", captured: { module: "{{from.module}}" } } } },
        { to: { element: { types: { anyOf: ["entities", "shared"] } } } },
      ],
    },
    {
      from: { element: { type: "entities" } },
      allow: [
        { to: { element: { type: "entities", captured: { entity: "{{from.entity}}" } } } },
        { to: { element: { type: "shared" } } },
      ],
    },
    {
      from: { element: { type: "shared" } },
      allow: { to: { element: { type: "shared" } } },
    },
  ],
};

export default tseslint.config(
  { ignores: ["dist", "node_modules"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  reactHooks.configs.flat.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { boundaries, import: importPlugin, "@eslint-community/eslint-comments": eslintComments },
    settings: {
      // Boundaries phân loại tệp qua đường dẫn thật, nên nó phải giải được alias "@/..." trước;
      // không có resolver thì mọi import dạng alias bị coi là gói ngoài và luật không bao giờ chạy.
      "import/resolver": { typescript: { project: "./tsconfig.json" } },
      "boundaries/elements": LAYERS.elements,
      "boundaries/include": ["src/**/*"],
    },
    rules: {
      "boundaries/dependencies": ["error", { default: "disallow", policies: LAYERS.policies }],
      "boundaries/no-unknown-files": "error",
      // Vòng import làm thứ tự khởi tạo module phụ thuộc vào ai được nạp trước: lỗi hiện ra dưới dạng
      // "undefined" ở một chỗ không liên quan. import() động để tách bundle không tính là vòng.
      "import/no-cycle": ["error", { allowUnsafeDynamicCyclicDependency: false }],
      // Tắt luật thì phải nói rõ tắt luật nào và vì sao, nếu không lần sau không ai dám bật lại.
      "@eslint-community/eslint-comments/no-unlimited-disable": "error",
      "@eslint-community/eslint-comments/require-description": ["error", { ignore: [] }],
      complexity: ["error", 15],
      "max-lines": ["error", { max: 600, skipBlankLines: true, skipComments: true }],
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      // Chuỗi rỗng và số 0 là giá trị hợp lệ trong dữ liệu (tên chi nhánh chưa đặt, 0 tài khoản),
      // nên || sẽ âm thầm thay chúng bằng giá trị mặc định.
      "no-restricted-syntax": [
        "error",
        {
          selector: "LogicalExpression[operator='||'] > JSXExpressionContainer",
          message: "Dùng ?? thay cho || khi lấy giá trị mặc định.",
        },
      ],
    },
  },
  {
    files: ["vite.config.ts", "eslint.config.js"],
    languageOptions: { globals: { process: "readonly" } },
  },
);
