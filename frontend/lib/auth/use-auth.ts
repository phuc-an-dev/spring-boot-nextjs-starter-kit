import useSWR from "swr";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { restClient } from "../httpClient";
import { HttpErrorResponse } from "@/models/http/HttpErrorResponse";
import { LoginRequest, UserResponse } from "@/models/backend";

interface AuthProps {
  middleware?: "auth" | "guest";
  redirectIfAuthenticated?: string;
}

const fetchCurrentUser = async (): Promise<UserResponse> => {
  try {
    return await restClient.getSession();
  } catch (error: any) {
    if (error?.response?.status !== 401) {
      throw error;
    }

    await restClient.refresh();
    return await restClient.getSession();
  }
};

export const useAuthGuard = ({
  middleware,
  redirectIfAuthenticated,
}: AuthProps) => {
  const router = useRouter();

  const {
    data: user,
    error,
    mutate,
  } = useSWR("/api/auth/me", fetchCurrentUser);

  const login = async ({
    onError,
    props,
  }: {
    onError: (errors: HttpErrorResponse | undefined) => void;
    props: LoginRequest;
  }) => {
    onError(undefined);
    // await csrf();
    return restClient.login(props)
      .then(() => mutate())
      .catch((err) => {
        const errors = err.response.data as HttpErrorResponse;
        onError(errors);
      });
  };

  // const csrf = async () => {
  //   await restClient.csrf();
  // };

  const logout = async () => {
    if (!error) {
      await restClient.logout().then(() => mutate(undefined, false));
    }

    window.location.pathname = "/auth/login";
  };

  useEffect(() => {
    // If middleware is 'guest' and we have a user, redirect
    if (middleware === "guest" && redirectIfAuthenticated && user) {
      router.push(redirectIfAuthenticated);
    }

    // If middleware is 'auth' and we have an error, logout
    if (middleware === "auth" && error) {
      logout();
    }
  }, [user, error]);

  return {
    user,
    login,
    logout,
    mutate,
  };
};
