# Data Layer — TanStack Query in Next.js / Vite

## Reads: Server Component prefetch or Hook encapsulation
Making a whole page `"use client"` and fetching with `useQuery` from mount means the browser downloads JS, mounts, *then* fetches — a client-side waterfall that costs a full round trip before anything useful renders. Prefetch on the server (Next.js) or wrap fetchers in custom hooks (Vite):

```tsx
// app/orders/page.tsx (Server Component, stays this way)
import { HydrationBoundary, QueryClient, dehydrate } from "@tanstack/react-query";
import { fetchOrders, ordersKeys, OrderList } from "@/modules/orders";

export default async function OrdersPage() {
  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ordersKeys.list(),
    queryFn: fetchOrders,
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <OrderList /> {/* client component, calls useOrders() — cache is already warm */}
    </HydrationBoundary>
  );
}
```
`OrderList` still calls `useOrders()` on the client (so it gets refetching, cache, mutation-sync) but the first paint doesn't wait on a client fetch — the data is already in the cache when it hydrates.

---

## Suspense and Error Boundaries, Not Inline `isLoading`/`error` Checks

Pair the prefetch above with `useSuspenseQuery` instead of `useQuery`, and let route `loading.tsx`/`error.tsx` (or React `<Suspense>` + `<ErrorBoundary>` in Vite) own loading and error states:

```ts
// modules/orders/api/use-orders.ts
export function useOrders() {
  return useSuspenseQuery({ queryKey: ordersKeys.list(), queryFn: fetchOrders, staleTime: 30_000 });
}
```
```tsx
// modules/orders/ui/order-list.tsx — no isLoading, no error, purely declarative render
export function OrderList() {
  const { data: orders } = useOrders();
  return <ul>{orders.map((o) => <OrderListItem key={o.id} order={o} />)}</ul>;
}
```

---

## Query Key Factory (Strictly No String Literals)

Never inline `["orders", "list"]` raw string arrays in components. Always define a typed factory:

```ts
// modules/orders/api/query-keys.ts
export const ordersKeys = {
  all: ["orders"] as const,
  lists: () => [...ordersKeys.all, "list"] as const,
  list: (filters?: Record<string, unknown>) => [...ordersKeys.lists(), filters] as const,
  detail: (id: string) => [...ordersKeys.all, "detail", id] as const,
};
```

This prevents typos and guarantees targeted cache invalidation (`queryClient.invalidateQueries({ queryKey: ordersKeys.lists() })`).

---

## Zero Hardcoded Strings & Centralized Constants

Never use inline string literals for auth status, role codes, permissions, resources, actions, or route paths:

```ts
// REJECTED:
if (session.status === "authenticated") { ... }
<Can I="READ" a="ACCOUNT" />

// CORRECT:
import { AUTH_STATUS, RESOURCES, ACTIONS, PERMISSIONS } from "@/shared/constants";
import { isAuthenticated } from "@/shared/lib/auth";

if (isAuthenticated(session)) { ... }
<Can I={ACTIONS.READ} a={RESOURCES.ACCOUNT} />
```

---

## Separation of Logic from Render & Zero Inline Handlers

UI components (`ui/*.tsx`) must be **strictly declarative and presentational** ("Clean Render"). They should focus only on layout, Tailwind styles, accessibility, and visual states.

### The Rule:
1. **Zero inline async handlers**: Never write giant inline `onSubmit={async (data) => { ... }}` or complex data transformation logic inside JSX props.
2. **Extract to Custom Controller Hooks (`hooks/use-*.ts`)**:
   - All state transitions, form state (`react-hook-form` / Zod), mutation hooks (`useMutation`), query hooks (`useQuery`), and event callbacks MUST live inside a dedicated hook (e.g. `use-order-list-controller.ts`, `use-create-order-form.ts`).
   - The UI component only invokes the hook, receives state and handler callbacks, and renders JSX.

```tsx
// Controller Hook: modules/orders/hooks/use-order-list-controller.ts
export function useOrderListController() {
  const { data: orders } = useOrders();
  const deleteMutation = useDeleteOrderMutation();

  const handleDelete = (id: string) => {
    deleteMutation.mutate(id);
  };

  return {
    orders,
    isDeleting: deleteMutation.isPending,
    handleDelete,
  };
}

// Presentational UI: modules/orders/ui/order-list.tsx (< 100 lines)
export function OrderList() {
  const { orders, isDeleting, handleDelete } = useOrderListController();

  return (
    <div className="space-y-4">
      {orders.map((order) => (
        <OrderItem key={order.id} order={order} onDelete={handleDelete} disabled={isDeleting} />
      ))}
    </div>
  );
}
```

---

## Component Complexity Budget (< 15 Cyclomatic Complexity & Budget)

1. **Cyclomatic Complexity < 15**:
   - ESLint rule `complexity: ["error", 15]` enforces this strictly.
   - If a component has multiple if/else branches, extract branch rendering into smaller sub-components.
2. **Component Size Budget (< 150–200 lines)**:
   - Pure UI component files must stay under ~150–200 lines. If a view grows past that, decompose it into focused sub-components.
3. **No Nested Ternary Hell**:
   - `a ? (b ? <C1 /> : <C2 />) : <C3 />` is forbidden. Use early returns or separate sub-components for clean readability.
