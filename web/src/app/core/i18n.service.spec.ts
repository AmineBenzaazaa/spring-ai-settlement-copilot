import { TestBed } from '@angular/core/testing';
import { I18nService, SUGGESTIONS } from './i18n.service';

describe('I18nService', () => {
  beforeEach(() => localStorage.clear());

  it('switches language, strings and suggestions, and remembers the choice', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.setLang('fr');
    expect(i18n.t('send')).toBe('Envoyer');
    expect(i18n.suggestions()).toEqual(SUGGESTIONS.fr);
    expect(localStorage.getItem('settlement-copilot.lang')).toBe('fr');
    expect(document.documentElement.lang).toBe('fr');
    i18n.setLang('en');
    expect(i18n.t('send')).toBe('Send');
  });

  it('restores a saved language', () => {
    localStorage.setItem('settlement-copilot.lang', 'fr');
    TestBed.resetTestingModule();
    expect(TestBed.inject(I18nService).lang()).toBe('fr');
  });
});
