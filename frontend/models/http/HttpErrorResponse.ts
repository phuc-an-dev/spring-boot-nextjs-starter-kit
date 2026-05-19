export interface HttpErrorResponse {
  success?: boolean;
  message: string;
  status: number;
  errorCode?: number;
  errors?: Record<string, string>;
  generalErrors?: string[];
}
