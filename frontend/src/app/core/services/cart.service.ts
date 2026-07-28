import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CartItem, Cart } from '../models/cart.model';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly cartSubject = new BehaviorSubject<Cart>(this.getEmptyCart());
  readonly cart$ = this.cartSubject.asObservable();
  private readonly isBrowser: boolean;

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this.isBrowser = isPlatformBrowser(platformId);
    if (this.isBrowser) {
      this.loadCartFromStorage();
    }
  }

  addToCart(product: Product, quantity: number = 1, size?: number, color?: string): void {
    if (quantity < 1 || product.stock < 1) {
      return;
    }

    const currentCart: Cart = {
      ...this.cartSubject.value,
      items: this.cartSubject.value.items.map(item => ({ ...item }))
    };
    const existingItem = currentCart.items.find(item => 
      item.productId === product.id && 
      item.size === size && 
      item.color === color
    );

    if (existingItem) {
      existingItem.quantity = Math.min(existingItem.quantity + quantity, product.stock);
    } else {
      const newItem: CartItem = {
        id: Date.now(),
        productId: product.id,
        productName: product.name,
        productSlug: product.slug,
        productImage: product.imageUrl,
        price: product.price,
        quantity: Math.min(quantity, product.stock),
        size,
        color,
        stock: product.stock,
        addedAt: new Date()
      };
      currentCart.items.push(newItem);
    }

    this.updateCart(currentCart);
  }

  removeFromCart(itemId: number): void {
    const currentCart = this.cartSubject.value;
    currentCart.items = currentCart.items.filter(item => item.id !== itemId);
    this.updateCart(currentCart);
  }

  updateQuantity(itemId: number, quantity: number): void {
    const currentCart = this.cartSubject.value;
    const item = currentCart.items.find(item => item.id === itemId);
    
    if (item) {
      if (quantity <= 0) {
        this.removeFromCart(itemId);
      } else {
        item.quantity = quantity;
        this.updateCart(currentCart);
      }
    }
  }

  clearCart(): void {
    this.cartSubject.next(this.getEmptyCart());
    this.saveCartToStorage();
  }

  getCartItemCount(): number {
    return this.cartSubject.value.items.reduce((total: number, item: CartItem) => total + item.quantity, 0);
  }

  getCartTotal(): number {
    return this.cartSubject.value.items.reduce((total: number, item: CartItem) => total + (item.price * item.quantity), 0);
  }

  isInCart(productId: number, size?: number, color?: string): boolean {
    return this.cartSubject.value.items.some(item => 
      item.productId === productId && 
      item.size === size && 
      item.color === color
    );
  }

  private updateCart(cart: Cart): void {
    cart.totalAmount = this.calculateTotal(cart.items);
    cart.totalItems = this.calculateTotalItems(cart.items);
    cart.updatedAt = new Date();
    
    this.cartSubject.next(cart);
    this.saveCartToStorage();
  }

  private calculateTotal(items: CartItem[]): number {
    return items.reduce((total: number, item: CartItem) => total + (item.price * item.quantity), 0);
  }

  private calculateTotalItems(items: CartItem[]): number {
    return items.reduce((total: number, item: CartItem) => total + item.quantity, 0);
  }

  private getEmptyCart(): Cart {
    return {
      id: Date.now(),
      items: [],
      totalAmount: 0,
      totalItems: 0,
      createdAt: new Date(),
      updatedAt: new Date()
    };
  }

  private loadCartFromStorage(): void {
    if (!this.isBrowser) {
      return;
    }

    const storedCart = localStorage.getItem('cart');
    if (storedCart) {
      try {
        const cart = JSON.parse(storedCart);
        this.cartSubject.next(cart);
      } catch (error) {
        console.error('Error loading cart from storage:', error);
        this.cartSubject.next(this.getEmptyCart());
      }
    }
  }

  private saveCartToStorage(): void {
    if (!this.isBrowser) {
      return;
    }

    try {
      localStorage.setItem('cart', JSON.stringify(this.cartSubject.value));
    } catch (error) {
      console.error('Error saving cart to storage:', error);
    }
  }
}
