# Enforcing the Layers

Conventions decay without enforcement. Use `eslint-plugin-boundaries` to turn the dependency rule into a build failure instead of a code-review reminder.

```js
// eslint.config.js
import boundaries from "eslint-plugin-boundaries";

export default [
  {
    plugins: { boundaries },
    settings: {
      "boundaries/elements": [
        { type: "app", pattern: "src/app/*" },
        { type: "module", pattern: "src/modules/*", capture: ["name"] },
        { type: "entity", pattern: "src/entities/*", capture: ["name"] },
        { type: "shared", pattern: "src/shared/*" },
      ],
    },
    rules: {
      "boundaries/element-types": [
        "error",
        {
          default: "disallow",
          rules: [
            { from: "app", allow: ["module", "entity", "shared"] },
            { from: "module", allow: ["entity", "shared"] }, // NOT other modules
            { from: "entity", allow: ["shared"] },            // NOT modules
            { from: "shared", allow: [] },
          ],
        },
      ],
    },
  },
];
```
This makes `modules/orders` importing from `modules/profile` a lint error, not a convention someone forgot.

---

## Zero Hardcoded String Literals (Không Hard-Type String)

Never use inline string literals for auth status, role codes, permissions, resources, actions, or route paths.

```ts
// REJECTED:
if (session.status === "authenticated") { ... }
<Can I="READ" a="ACCOUNT" />
<RequirePermission resource="ACCOUNT" action="CREATE" />
const queryKey = ["accounts", id];

// CORRECT:
import { AUTH_STATUS, RESOURCES, ACTIONS, PERMISSIONS } from "@/shared/constants";
import { isAuthenticated } from "@/shared/lib/auth";

if (isAuthenticated(session)) { ... }
<Can I={ACTIONS.READ} a={RESOURCES.ACCOUNT} />
<RequirePermission resource={RESOURCES.ACCOUNT} action={ACTIONS.CREATE} />
const queryKey = accountQueryKeys.detail(id);
```

Using centralized constants from `@/shared/constants/` prevents typo bugs, makes refactoring instant via TypeScript symbols, and guarantees frontend and backend constants remain synchronized.

---

## Separation of Logic from Render (Tách Biệt Logic Khỏi Render)

UI components (`ui/*.tsx`) must be **strictly declarative and presentational** ("Clean Render"). They should focus only on layout, Tailwind styles, accessibility, and visual states.

### The Rule:
1. **Zero inline async handlers**: Never write giant inline `onSubmit={async (data) => { ... }}` in JSX props.
2. **Extract to Custom Hooks (`hooks/use-*.ts`)**: Form state, Zod validation, TanStack Query mutations/queries, and multi-step workflows belong in a custom controller hook.

```tsx
// REJECTED: Logic, mutation, state, and render all tangled in one component
export function BadAccountForm() {
  const [loading, setLoading] = useState(false);
  const queryClient = useQueryClient();
  const form = useForm({ resolver: zodResolver(accountSchema) });

  const onSubmit = async (data) => {
    setLoading(true);
    try {
      await apiFetch("/api/accounts", { method: "POST", body: data });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      toast.success("Created");
    } catch (e) {
      toast.error("Failed");
    } finally {
      setLoading(false);
    }
  };

  return <form onSubmit={form.handleSubmit(onSubmit)}>...</form>;
}

// CORRECT: UI component is clean, purely presentational
// 1. Controller Hook: hooks/use-account-form-controller.ts
export function useAccountFormController(onSuccess?: () => void) {
  const form = useForm<AccountFormValues>({ resolver: zodResolver(accountSchema) });
  const createMutation = useCreateAccountMutation({
    onSuccess: () => {
      form.reset();
      onSuccess?.();
    }
  });

  const handleSubmit = form.handleSubmit((data) => createMutation.mutate(data));

  return { form, isSubmitting: createMutation.isPending, handleSubmit };
}

// 2. Presentational UI Component: ui/account-form.tsx (< 100 lines)
export function AccountForm({ onSuccess }: AccountFormProps) {
  const { form, isSubmitting, handleSubmit } = useAccountFormController(onSuccess);

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormField name="email" render={({ field }) => <Input {...field} />} />
        <Button type="submit" disabled={isSubmitting}>Submit</Button>
      </form>
    </Form>
  );
}
```

---

## Keeping Components Clean (< 15 Cyclomatic Complexity & Budget)

1. **Cyclomatic Complexity < 15**:
   - ESLint rule `complexity: ["error", 15]` enforces this strictly.
   - If a component has multiple if/else branches, extract branch rendering into smaller sub-components.
2. **Component Size Budget (< 150–200 lines)**:
   - Pure UI component files must stay under ~150–200 lines. If a view grows past that, decompose it into focused sub-components (`<AccountListHeader>`, `<AccountListTable>`, `<AccountListPagination>`).
3. **No Nested Ternary Hell**:
   - `a ? (b ? <C1 /> : <C2 />) : <C3 />` is forbidden.
   - Use early returns or separate sub-components for clean readability.

```js
// eslint.config.js
export default [
  {
    rules: {
      complexity: ["error", 15],
      "max-lines": ["warn", { max: 200, skipBlankLines: true, skipComments: true }],
    },
  },
];
```

---

## No Circular Imports

```js
// eslint.config.js
export default [
  {
    rules: {
      "import/no-cycle": "error",
    },
  },
];
```
Two files importing each other breaks under ESM's live-binding evaluation order. Move shared types/hooks down into `model/` or `entities/` so dependencies only point one way.

---

## No Suppression Comment Without a Reason

```js
// eslint.config.js
import eslintComments from "@eslint-community/eslint-plugin-eslint-comments";

export default [
  {
    plugins: { "eslint-comments": eslintComments },
    rules: {
      "eslint-comments/require-description": ["error", { ignore: [] }],
      "@typescript-eslint/ban-ts-comment": [
        "error",
        { "ts-expect-error": "allow-with-description", "ts-ignore": true, minimumDescriptionLength: 10 },
      ],
    },
  },
];
```

---

## File Naming Conventions
| Item | Convention |
|---|---|
| Files/folders | kebab-case (`account-status-badge.tsx`) |
| Components | PascalCase export (`AccountStatusBadge`) |
| Hooks | `use-*.ts` file, `useCamelCase` export (`useAccountController`) |
| Imports | `@/*` -> `src/*`, never relative chains like `../../../` |
