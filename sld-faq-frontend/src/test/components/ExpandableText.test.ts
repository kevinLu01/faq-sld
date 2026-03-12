import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ExpandableText from '@/components/ExpandableText.vue'

// A text that exceeds the default maxLength of 200 characters
const LONG_TEXT = 'A'.repeat(201)
// A text that is within the default maxLength
const SHORT_TEXT = 'Hello world'

describe('ExpandableText', () => {
  it('renders_shortText_withoutToggleButton', () => {
    const wrapper = mount(ExpandableText, {
      props: { text: SHORT_TEXT },
    })

    // The toggle button should not exist when the text is short enough
    expect(wrapper.find('.toggle-btn').exists()).toBe(false)
  })

  it('renders_longText_withToggleButton', () => {
    const wrapper = mount(ExpandableText, {
      props: { text: LONG_TEXT },
    })

    const toggleBtn = wrapper.find('.toggle-btn')
    expect(toggleBtn.exists()).toBe(true)
    expect(toggleBtn.text()).toBe('展开全文')
  })

  it('click_toggleButton_expandsText', async () => {
    const wrapper = mount(ExpandableText, {
      props: { text: LONG_TEXT },
    })

    const toggleBtn = wrapper.find('.toggle-btn')
    await toggleBtn.trigger('click')

    // After expanding, the button label changes to 收起
    expect(wrapper.find('.toggle-btn').text()).toBe('收起')
    // The collapsed class should be removed from the text content
    expect(wrapper.find('.text-content').classes()).not.toContain('collapsed')
  })

  it('click_again_collapsesText', async () => {
    const wrapper = mount(ExpandableText, {
      props: { text: LONG_TEXT },
    })

    const toggleBtn = wrapper.find('.toggle-btn')
    // Expand first
    await toggleBtn.trigger('click')
    // Then collapse
    await toggleBtn.trigger('click')

    expect(wrapper.find('.toggle-btn').text()).toBe('展开全文')
    expect(wrapper.find('.text-content').classes()).toContain('collapsed')
  })

  it('defaultMaxLength_is200', () => {
    // A text of exactly 200 characters should NOT show the toggle button
    const exactlyMaxLength = 'B'.repeat(200)
    const wrapperAtLimit = mount(ExpandableText, {
      props: { text: exactlyMaxLength },
    })
    expect(wrapperAtLimit.find('.toggle-btn').exists()).toBe(false)

    // A text of 201 characters SHOULD show the toggle button
    const oneOverLimit = 'B'.repeat(201)
    const wrapperOverLimit = mount(ExpandableText, {
      props: { text: oneOverLimit },
    })
    expect(wrapperOverLimit.find('.toggle-btn').exists()).toBe(true)
  })
})
