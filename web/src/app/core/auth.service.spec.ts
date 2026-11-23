import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService, decodeUser } from './auth.service';
import { authGuard } from './auth.guard';

export function fakeJwt(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.signature`;
}

const atlasToken = fakeJwt({ sub: 'atlas', role: 'MERCHANT', mid: 1, exp: Math.floor(Date.now() / 1000) + 900 });

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in on the settlement platform and exposes the user from the token', () => {
    let username = '';
    auth.login('atlas', 'demo-pass-123').subscribe((u) => (username = u.username));
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ username: 'atlas', password: 'demo-pass-123' });
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: atlasToken, refreshToken: 'r1', expiresIn: 900 });

    expect(username).toBe('atlas');
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.user()).toMatchObject({ role: 'MERCHANT', merchantId: 1, merchantName: null });
    expect(JSON.parse(sessionStorage.getItem('settlement-copilot.session')!).refreshToken).toBe('r1');

    // then the merchant's display name, for the header and the user menu
    const me = http.expectOne('/api/auth/me');
    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${atlasToken}`);
    me.flush({ username: 'atlas', merchantName: 'Atlas Home' });
    expect(auth.user()?.merchantName).toBe('Atlas Home');
  });

  it('adds the bearer token to API calls', () => {
    auth.login('atlas', 'x').subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: atlasToken, refreshToken: 'r', expiresIn: 1 });
    http.expectOne('/api/auth/me').flush({}, { status: 500, statusText: 'boom' }); // best effort: ignored
    TestBed.inject(HttpClient).get('/api/copilot/info').subscribe();
    expect(http.expectOne('/api/copilot/info').request.headers.get('Authorization')).toBe(`Bearer ${atlasToken}`);
  });

  it('refreshes the access token, and signs out when refresh fails', async () => {
    auth.login('atlas', 'x').subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: atlasToken, refreshToken: 'r1', expiresIn: 1 });
    http.expectOne('/api/auth/me').flush({ merchantName: 'Atlas Home' });

    const ok = auth.refresh();
    const refresh = http.expectOne('/api/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    refresh.flush({ accessToken: atlasToken, refreshToken: 'r2', expiresIn: 1 });
    expect(await ok).toBe(true);

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const failed = auth.refresh();
    http.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await failed).toBe(false);
    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('cannot refresh without a session', async () => {
    expect(await auth.refresh()).toBe(false);
  });

  it('guards the chat route', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(String(result)).toBe('/login');
  });
});

describe('decodeUser', () => {
  it('reads staff claims and rejects garbage', () => {
    const staff = decodeUser(fakeJwt({ sub: 'ops', role: 'STAFF', exp: 1 }));
    expect(staff).toEqual({ username: 'ops', role: 'STAFF', merchantId: null, expiresAt: 1000 });
    expect(decodeUser('not-a-token')).toBeNull();
  });
});
