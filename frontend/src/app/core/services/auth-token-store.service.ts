import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthTokenStore {
  private accessToken: string | null = null;

  get(): string | null {
    if (this.accessToken && this.isExpired(this.accessToken)) {
      this.accessToken = null;
    }
    return this.accessToken;
  }

  set(token: string): void {
    this.accessToken = token;
  }

  clear(): void {
    this.accessToken = null;
  }

  private isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(
        decodeURIComponent(
          atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
            .split('')
            .map(character => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`)
            .join('')
        )
      ) as { exp?: number };
      return payload.exp != null && payload.exp * 1000 <= Date.now() + 5000;
    } catch {
      return true;
    }
  }
}
