import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.ensureSession().pipe(
    map(authenticated => authenticated
      ? true
      : router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } }))
  );
};
