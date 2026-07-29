import { provideHttpClient } from '@angular/common/http';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { Product } from '../models/product.model';
import { CartService } from './cart.service';
import { AuthService } from './auth.service';

const product: Product = {
  id: 1,
  name: 'Sapatilha',
  slug: 'sapatilha',
  description: 'Produto para teste',
  price: 129.9,
  imageUrl: '/assets/images/product-placeholder.svg',
  category: 'Sapatilhas',
  categorySlug: 'sapatilhas',
  featured: true,
  active: true,
  availableSizes: [35, 36],
  availableColors: ['Preto'],
  stock: 2
};

describe('CartService', () => {
  let service: CartService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        CartService,
        provideHttpClient(),
        {
          provide: AuthService,
          useValue: {
            isLoggedIn$: new BehaviorSubject(false),
            isAuthenticated: () => false
          }
        },
        { provide: PLATFORM_ID, useValue: 'browser' }
      ]
    });
    service = TestBed.inject(CartService);
  });

  it('adds a product and calculates totals', () => {
    service.addToCart(product, 1, 35, 'Preto');

    expect(service.getCartItemCount()).toBe(1);
    expect(service.getCartTotal()).toBe(129.9);
    expect(service.isInCart(product.id, 35, 'Preto')).toBe(true);
  });

  it('does not allow a quantity above stock', () => {
    service.addToCart(product, 5, 35, 'Preto');

    expect(service.getCartItemCount()).toBe(2);
  });
});
