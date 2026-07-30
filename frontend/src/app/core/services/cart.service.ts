import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import {
  BehaviorSubject,
  catchError,
  concatMap,
  distinctUntilChanged,
  from,
  map,
  Observable,
  of,
  tap
} from 'rxjs';
import { Cart } from '../models/cart.model';
import { Product } from '../models/product.model';
import { AuthService } from './auth.service';

interface ApiCartItem {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  productSlug: string;
  productImage: string;
  price: number;
  quantity: number;
  size: number;
  color: string;
  availableStock: number;
  lineTotal: number;
}

interface ApiCart {
  id: number;
  items: ApiCartItem[];
  subtotal: number;
  totalItems: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly apiUrl = '/api/cart';
  private readonly cartSubject = new BehaviorSubject<Cart>(this.emptyCart());
  private readonly isBrowser: boolean;
  readonly cart$ = this.cartSubject.asObservable();

  constructor(
    @Inject(PLATFORM_ID) platformId: object,
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
    this.loadGuestCart();
    this.authService.isLoggedIn$
      .pipe(distinctUntilChanged())
      .subscribe(isLoggedIn => {
        if (isLoggedIn) {
          this.synchronizeGuestCart();
        } else {
          this.loadGuestCart();
        }
      });
  }

  addToCart(
    product: Product,
    quantity = 1,
    size?: number,
    color?: string,
    variantId?: number
  ): Observable<Cart> {
    if (quantity < 1 || product.stock < 1) {
      return of(this.cartSubject.value);
    }
    if (this.authService.isAuthenticated() && variantId) {
      return this.http
        .post<ApiCart>(`${this.apiUrl}/items`, { variantId, quantity })
        .pipe(map(cart => this.storeApiCart(cart)));
    }

    const currentCart = this.cloneCart();
    const existingItem = currentCart.items.find(item =>
      item.productId === product.id
      && item.size === size
      && item.color === color
    );
    if (existingItem) {
      existingItem.quantity = Math.min(existingItem.quantity + quantity, product.stock);
    } else {
      currentCart.items.push({
        id: Date.now(),
        productId: product.id,
        variantId,
        productName: product.name,
        productSlug: product.slug,
        productImage: product.imageUrl,
        price: product.price,
        quantity: Math.min(quantity, product.stock),
        size,
        color,
        stock: product.stock,
        addedAt: new Date()
      });
    }
    this.updateGuestCart(currentCart);
    return of(currentCart);
  }

  refresh(): Observable<Cart> {
    if (!this.authService.isAuthenticated()) {
      return of(this.cartSubject.value);
    }
    return this.http.get<ApiCart>(this.apiUrl).pipe(map(cart => this.storeApiCart(cart)));
  }

  removeFromCart(itemId: number): Observable<Cart> {
    if (this.authService.isAuthenticated()) {
      return this.http
        .delete<ApiCart>(`${this.apiUrl}/items/${itemId}`)
        .pipe(map(cart => this.storeApiCart(cart)));
    }
    const cart = this.cloneCart();
    cart.items = cart.items.filter(item => item.id !== itemId);
    this.updateGuestCart(cart);
    return of(cart);
  }

  updateQuantity(itemId: number, quantity: number): Observable<Cart> {
    if (quantity <= 0) {
      return this.removeFromCart(itemId);
    }
    if (this.authService.isAuthenticated()) {
      return this.http
        .patch<ApiCart>(`${this.apiUrl}/items/${itemId}`, { quantity })
        .pipe(map(cart => this.storeApiCart(cart)));
    }
    const cart = this.cloneCart();
    const item = cart.items.find(candidate => candidate.id === itemId);
    if (item) {
      item.quantity = Math.min(quantity, item.stock);
      this.updateGuestCart(cart);
    }
    return of(cart);
  }

  clearCart(): Observable<Cart> {
    if (this.authService.isAuthenticated()) {
      return this.http.delete<ApiCart>(this.apiUrl).pipe(map(cart => this.storeApiCart(cart)));
    }
    const cart = this.emptyCart();
    this.cartSubject.next(cart);
    this.saveGuestCart();
    return of(cart);
  }

  getCartItemCount(): number {
    return this.cartSubject.value.totalItems;
  }

  getCartTotal(): number {
    return this.cartSubject.value.totalAmount;
  }

  isInCart(productId: number, size?: number, color?: string): boolean {
    return this.cartSubject.value.items.some(item =>
      item.productId === productId && item.size === size && item.color === color
    );
  }

  private synchronizeGuestCart(): void {
    const guestItems = this.cartSubject.value.items.filter(item => item.variantId);
    if (!guestItems.length) {
      this.refresh().pipe(catchError(() => of(this.cartSubject.value))).subscribe();
      return;
    }
    from(guestItems).pipe(
      concatMap(item => this.http.post<ApiCart>(`${this.apiUrl}/items`, {
        variantId: item.variantId,
        quantity: item.quantity
      })),
      tap(cart => this.storeApiCart(cart)),
      catchError(() => this.refresh())
    ).subscribe({
      complete: () => this.removeGuestStorage()
    });
  }

  private storeApiCart(apiCart: ApiCart): Cart {
    const cart: Cart = {
      id: apiCart.id,
      items: apiCart.items.map(item => ({
        id: item.id,
        productId: item.productId,
        variantId: item.variantId,
        productName: item.productName,
        productSlug: item.productSlug,
        productImage: item.productImage,
        price: item.price,
        quantity: item.quantity,
        size: item.size,
        color: item.color,
        stock: item.availableStock,
        lineTotal: item.lineTotal
      })),
      totalAmount: apiCart.subtotal,
      totalItems: apiCart.totalItems
    };
    this.cartSubject.next(cart);
    return cart;
  }

  private cloneCart(): Cart {
    return {
      ...this.cartSubject.value,
      items: this.cartSubject.value.items.map(item => ({ ...item }))
    };
  }

  private updateGuestCart(cart: Cart): void {
    cart.totalAmount = cart.items.reduce((total, item) => total + item.price * item.quantity, 0);
    cart.totalItems = cart.items.reduce((total, item) => total + item.quantity, 0);
    cart.updatedAt = new Date();
    this.cartSubject.next(cart);
    this.saveGuestCart();
  }

  private emptyCart(): Cart {
    return {
      id: Date.now(),
      items: [],
      totalAmount: 0,
      totalItems: 0,
      createdAt: new Date(),
      updatedAt: new Date()
    };
  }

  private loadGuestCart(): void {
    if (!this.isBrowser) {
      return;
    }
    const storedCart = localStorage.getItem('femstep_guest_cart');
    if (!storedCart) {
      this.cartSubject.next(this.emptyCart());
      return;
    }
    try {
      this.cartSubject.next(JSON.parse(storedCart) as Cart);
    } catch {
      this.removeGuestStorage();
      this.cartSubject.next(this.emptyCart());
    }
  }

  private saveGuestCart(): void {
    if (this.isBrowser) {
      localStorage.setItem('femstep_guest_cart', JSON.stringify(this.cartSubject.value));
    }
  }

  private removeGuestStorage(): void {
    if (this.isBrowser) {
      localStorage.removeItem('femstep_guest_cart');
    }
  }
}
