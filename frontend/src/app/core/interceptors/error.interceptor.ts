import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { MessageService } from "primeng/api";
import { inject } from "@angular/core";
import { catchError, throwError } from "rxjs";
import { Router } from "@angular/router";
import { AuthSessionService } from "../services/auth-session.service";

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const messageService = inject(MessageService, { optional: true });
  const router = inject(Router);
  const session = inject(AuthSessionService);

  const isAuthEndpoint =
    req.url.includes("/auth/login") || req.url.includes("/auth/register") || req.url.includes("/auth/accept-invite");

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isAuthEndpoint) {
        session.handleUnauthorized(router.url);
        messageService?.add({
          severity: "warn",
          summary: "Session expired",
          detail: "Please sign in again."
        });
        return throwError(() => error);
      }

      const message: string = error.error?.message ?? "Unexpected error. Please retry.";

      if (error.status === 402 && message.includes("Trial expired")) {
        // Always allow account/GDPR pages — users must be able to export or delete
        // their data regardless of billing status (GDPR Art. 17 / Art. 20).
        const currentPath = router.url.split("?")[0];
        const isGdprPage = currentPath === "/account";
        if (!isGdprPage) {
          void router.navigate(["/billing/upgrade"]);
        }
        return throwError(() => error);
      }

      // Plan-gate and quota errors (403) are handled inline by components — suppress the generic toast.
      const reason: string | undefined = error.error?.reason;
      if (error.status === 403 && (reason?.startsWith("PLAN_FEATURE_") || reason === "QUOTA_EXCEEDED" || reason === "PLAN_GATE")) {
        return throwError(() => error);
      }

      messageService?.add({
        severity: "error",
        summary: "Request failed",
        detail: message
      });
      return throwError(() => error);
    })
  );
};
