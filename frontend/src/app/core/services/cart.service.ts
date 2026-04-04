import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { CartItem, Cart } from '../models/cart.model';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private cartSubject = new BehaviorSubject<Cart>(this.getEmptyCart());
  cart$ = this.cartSubject.asObservable();

  constructor() {
    this.loadCartFromStorage();
  }

  addToCart(product: Product, quantity: number = 1, size?: number, color?: string): void {
    const currentCart = this.cartSubject.value;
    const existingItem = currentCart.items.find(item => 
      item.productId === product.id && 
      item.size === size && 
      item.color === color
    );

    if (existingItem) {
      existingItem.quantity += quantity;
    } else {
      const newItem: CartItem = {
        id: Date.now(),
        productId: product.id,
        productName: product.name,
        productSlug: product.slug,
        productImage: product.imageUrl,
        price: product.price,
        quantity: quantity,
        size: size,
        color: color,
        stock: product.stock
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
    const cart = this.cartSubject.value;
    localStorage.setItem('cart', JSON.stringify(cart));
  }
}
