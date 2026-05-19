import { RestApplicationClient } from '@/models/backend'
import Axios from 'axios'

type ApiSuccessResponse<T> = {
  success: true
  data: T
}

type UnwrappedApiResponse<T> = T extends ApiSuccessResponse<infer D> ? D : T

export function unwrapApiResponse<T>(payload: T): UnwrappedApiResponse<T> {
  if (
    payload &&
    typeof payload === 'object' &&
    'success' in payload &&
    'data' in payload
  ) {
    return (payload as ApiSuccessResponse<UnwrappedApiResponse<T>>).data
  }

  return payload as UnwrappedApiResponse<T>
}

const httpClient = Axios.create({
  baseURL: process.env.NEXT_PUBLIC_BASE_URL!, 
  headers: {
      'X-Requested-With': 'XMLHttpRequest',
      'Content-Type': 'application/json',
      'Accept': 'application/json'
  },
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  withXSRFToken: true,
})

const backendClient =  Axios.create({
  baseURL: process.env.NEXT_PUBLIC_BASE_URL!, 
  headers: {
      'X-Requested-With': 'XMLHttpRequest',
      'Content-Type': 'application/json',
      'Accept': 'application/json'
  },
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  withXSRFToken: true
})
backendClient.interceptors.response.use((response) => {
  return unwrapApiResponse(response.data)
})

export const restClient = new RestApplicationClient(backendClient)

export default httpClient
