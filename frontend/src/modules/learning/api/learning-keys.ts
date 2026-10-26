export const learningKeys = {
  all: ["learning"] as const,
  myClasses: () => [...learningKeys.all, "my-classes"] as const,
  myInvoices: () => [...learningKeys.all, "my-invoices"] as const,
} as const;
