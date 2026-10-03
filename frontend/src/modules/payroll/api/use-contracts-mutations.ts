import { contractApi, payrollKeys, type CreateContractPayload, type UpdateContractPayload } from "@/entities/payroll";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useContractsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: payrollKeys.contracts.all });
  };
}

export function useCreateContract() {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: ({ payload, file }: { payload: CreateContractPayload; file: File | null }) =>
      contractApi.createContract(payload, file),
    onSuccess: invalidate,
  });
}

export function useUpdateContract(contractId: string) {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: ({ payload, file }: { payload: UpdateContractPayload; file: File | null }) =>
      contractApi.updateContract(contractId, payload, file),
    onSuccess: invalidate,
  });
}

export function useTerminateContract() {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: (contractId: string) => contractApi.terminateContract(contractId),
    onSuccess: invalidate,
  });
}
