import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, map, tap } from 'rxjs';
import { SessionUser } from './models';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

const STORAGE_KEY = 'settlement-copilot.session';

/**
 * Signs in on the settlement platform (POST /api/auth/login, proxied by nginx) and keeps its JWT
 * in a signal. The copilot accepts that same token, so there is one login for both apps.
 * sessionStorage: the session ends with the tab, and the token never sits in a cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokens = signal<TokenResponse | null>(this.restore());
  readonly accessToken = computed(() => this.tokens()?.accessToken ?? null);
  readonly user = computed<SessionUser | null>(() => {
    const token = this.accessToken();
    return token ? decodeUser(token) : null;
  });
  readonly isLoggedIn = computed(() => {
    const user = this.user();
    return !!user && user.expiresAt > Date.now();
  });

  login(username: string, password: string): Observable<SessionUser> {
    return this.http.post<TokenResponse>('/api/auth/login', { username, password }).pipe(
      tap((t) => this.store(t)),
      map(() => this.user()!),
    );
  }

  /** Swaps the refresh token for a new access token. Resolves false (and signs out) when it fails. */
  async refresh(): Promise<boolean> {
    const refreshToken = this.tokens()?.refreshToken;
    if (!refreshToken) {
      return false;
    }
    try {
      const t = await firstValueFrom(this.http.post<TokenResponse>('/api/auth/refresh', { refreshToken }));
      this.store(t);
      return true;
    } catch {
      this.logout();
      return false;
    }
  }

  logout(): void {
    this.tokens.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable */
    }
    void this.router.navigate(['/login']);
  }

  private store(t: TokenResponse): void {
    this.tokens.set(t);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(t));
    } catch {
      /* storage unavailable: the session lives in memory only */
    }
  }

  private restore(): TokenResponse | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as TokenResponse) : null;
    } catch {
      return null;
    }
  }
}

/** Reads the claims for display only; the server verifies the signature on every request. */
export function decodeUser(token: string): SessionUser | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=')));
    return {
      username: json.sub,
      role: json.role,
      merchantId: json.mid ?? null,
      expiresAt: (json.exp ?? 0) * 1000,
    };
  } catch {
    return null;
  }
}
