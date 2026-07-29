import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Product } from '../../../core/models/product.model';
import { CartService } from '../../../core/services/cart.service';
import { AuthService } from '../../../core/services/auth.service';
import { FavoriteService } from '../../../core/services/favorite.service';
import { ProductService } from '../../../core/services/product.service';
import { ProductDetailPageComponent } from './product-detail-page.component';

registerLocaleData(localePt);

const product: Product = {
  id: 1,
  name: 'Scarpin Celeste',
  slug: 'scarpin-celeste',
  description: 'Produto de teste',
  shortDescription: 'Confortável e elegante.',
  price: 219.9,
  imageUrl: '/assets/images/products/scarpin-celeste.png',
  imageAlt: 'Scarpin Celeste',
  category: 'Saltos',
  categorySlug: 'saltos',
  brand: 'FemStep',
  material: 'Tecido',
  featured: false,
  active: true,
  availableSizes: [36],
  availableColors: ['Prata'],
  stock: 4,
  variants: [{
    id: 10,
    sku: 'FS-TESTE-36-PT',
    size: 36,
    color: 'Prata',
    availableStock: 4,
    price: 219.9,
    active: true
  }],
  images: [{
    id: 1,
    productId: 1,
    url: '/assets/images/products/scarpin-celeste.png',
    altText: 'Scarpin Celeste',
    displayOrder: 0,
    mainImage: true
  }]
};

describe('ProductDetailPageComponent', () => {
  let fixture: ComponentFixture<ProductDetailPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ slug: product.slug })) }
        },
        {
          provide: ProductService,
          useValue: { getProductBySlug: () => of(product) }
        },
        {
          provide: CartService,
          useValue: { addToCart: () => of({}) }
        },
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => false }
        },
        {
          provide: FavoriteService,
          useValue: {
            contains: () => of(false),
            add: () => of(undefined),
            remove: () => of(undefined)
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetailPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders the product after the asynchronous response', () => {
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('Scarpin Celeste');
    expect(content).toContain('R$');
    expect(content).not.toContain('Carregando produto');
  });

  it('requires a size and color before adding to the cart', () => {
    const component = fixture.componentInstance;

    component.addToCart();

    expect(component.feedbackMessage).toContain('tamanho e a cor');
  });
});
