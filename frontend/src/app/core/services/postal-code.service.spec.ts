import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PostalCodeService } from './postal-code.service';

describe('PostalCodeService', () => {
  let service: PostalCodeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PostalCodeService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PostalCodeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('normalizes the CEP and maps the ViaCEP address', () => {
    service.lookup('01001-000').subscribe(address => {
      expect(address).toEqual({
        postalCode: '01001000',
        street: 'Praça da Sé',
        neighborhood: 'Sé',
        city: 'São Paulo',
        state: 'SP'
      });
    });

    http.expectOne('https://viacep.com.br/ws/01001000/json/').flush({
      cep: '01001-000',
      logradouro: 'Praça da Sé',
      bairro: 'Sé',
      localidade: 'São Paulo',
      uf: 'SP'
    });
  });

  it('rejects invalid CEPs without making a request', () => {
    service.lookup('123').subscribe({
      error: error => expect(error.message).toBe('CEP inválido')
    });
  });
});
