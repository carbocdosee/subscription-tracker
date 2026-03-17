import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthSessionService } from "../services/auth-session.service";

export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  if (session.currentToken()) {
    return true;
  }
  return router.createUrlTree(["/auth"], {
    queryParams: { redirect: state.url }
  });
};

export const guestGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  if (!session.currentToken()) {
    return true;
  }
  return router.createUrlTree(["/dashboard"]);
};
