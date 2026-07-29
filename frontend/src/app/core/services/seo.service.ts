import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { Product } from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class SeoService {
  constructor(
    private readonly title: Title,
    private readonly meta: Meta,
    @Inject(DOCUMENT) private readonly document: Document
  ) {}

  setProduct(product: Product): void {
    const title = product.seoTitle?.trim() || `${product.name} | FemStep Store`;
    const description = product.seoDescription?.trim()
      || product.shortDescription?.trim()
      || product.description?.trim()
      || `Conheça ${product.name} na FemStep Store.`;
    const url = this.absoluteUrl(`/product/${product.slug}`);
    const image = this.absoluteUrl(product.imageUrl);

    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:type', content: 'product' });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:image', content: image });
    this.setCanonical(url);
    this.setStructuredData({
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: product.name,
      description,
      image: [image],
      sku: product.variants?.[0]?.sku,
      brand: { '@type': 'Brand', name: product.brand || 'FemStep' },
      offers: {
        '@type': 'Offer',
        priceCurrency: 'BRL',
        price: product.price.toFixed(2),
        availability: product.stock > 0
          ? 'https://schema.org/InStock'
          : 'https://schema.org/OutOfStock',
        url
      }
    });
  }

  private setCanonical(url: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.rel = 'canonical';
      this.document.head.appendChild(link);
    }
    link.href = url;
  }

  private setStructuredData(value: object): void {
    this.document.getElementById('product-structured-data')?.remove();
    const script = this.document.createElement('script');
    script.id = 'product-structured-data';
    script.type = 'application/ld+json';
    script.text = JSON.stringify(value);
    this.document.head.appendChild(script);
  }

  private absoluteUrl(path: string): string {
    const origin = this.document.location?.origin || '';
    return new URL(path, origin || 'http://localhost').toString();
  }
}
