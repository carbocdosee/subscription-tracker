import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { inject } from "@angular/core";
import { Router } from "@angular/router";
import { catchError, switchMap, throwError } from "rxjs";
import { AuthResponse } from "../../shared/models";
import { AuthSessionService } from "../services/auth-session.service";
import { TrackerApiService } from "../services/tracker-api.service";

const AUTH_PATH = "/auth/";

function withBearer(req: Parameters<HttpInterceptorFn>[0], token: string | null) {
  if (!token) return req;
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authSession = inject(AuthSessionService);
  const api = inject(TrackerApiService);
  const router = inject(Router);

  return next(withBearer(req, authSession.currentToken())).pipe(
    catchError(error => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.includes(AUTH_PATH)
      ) {
        return api.refreshToken().pipe(
          switchMap((response: AuthResponse) => {
            authSession.setToken(response.accessToken);
            return next(withBearer(req, response.accessToken));
          }),
          catchError(refreshError => {
            authSession.logout();
            void router.navigate(["/auth"]);
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
