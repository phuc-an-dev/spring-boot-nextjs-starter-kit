import { unwrapApiResponse } from "./httpClient";

type Wrapped<T> = {
  success: true;
  data: T;
};

const wrappedUser = unwrapApiResponse<Wrapped<{ id: number }>>({
  success: true,
  data: { id: 7 },
});
const legacyUser = unwrapApiResponse<{ id: number }>({ id: 9 });

const wrappedId: number = wrappedUser.id;
const legacyId: number = legacyUser.id;

if (wrappedId !== 7) {
  throw new Error("Wrapped API responses should unwrap to their data payload");
}

if (legacyId !== 9) {
  throw new Error("Legacy API responses should pass through unchanged");
}
