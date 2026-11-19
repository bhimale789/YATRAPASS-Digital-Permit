import { isAxiosError } from 'axios';
import type { ApiError } from '../types';

/** Extracts a user-presentable message from an API error. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (isAxiosError<ApiError>(error)) {
    const data = error.response?.data;
    if (data && typeof data === 'object' && 'message' in data) {
      if (data.fieldErrors?.length) {
        return `${data.message}: ${data.fieldErrors.map((f) => `${f.field} ${f.message}`).join('; ')}`;
      }
      return data.message;
    }
    if (!error.response) return 'Cannot reach the server. Check your connection.';
  }
  return fallback;
}

/** Field-level validation messages keyed by field name. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (isAxiosError<ApiError>(error) && error.response?.data?.fieldErrors) {
    return Object.fromEntries(error.response.data.fieldErrors.map((f) => [f.field, f.message]));
  }
  return {};
}
