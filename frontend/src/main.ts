import { bootstrapApplication } from "@angular/platform-browser";
import { provideAnimations } from "@angular/platform-browser/animations";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { AppComponent } from "./app/app.component";
import { appRoutes } from "./app/app.routes";
import { authInterceptor } from "./app/core/interceptors/auth.interceptor";
import { errorInterceptor } from "./app/core/interceptors/error.interceptor";
import { ConfirmationService, MessageService } from "primeng/api";

bootstrapApplication(AppComponent, {
  providers: [
    provideAnimations(),
    provideRouter(appRoutes),
    provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
    MessageService,
    ConfirmationService
  ]
}).catch((error: unknown) => {
  // Top-level bootstrap diagnostics.
  console.error("Application bootstrap failed", error);
});
