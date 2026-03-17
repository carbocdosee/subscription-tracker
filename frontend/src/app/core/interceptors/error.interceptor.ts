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
        void router.navigate(["/billing/upgrade"]);
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
