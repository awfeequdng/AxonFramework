/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.examples.addressbook.web;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.sample.app.api.*;
import org.axonframework.sample.app.query.AddressEntry;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Jettro Coenradie
 */
@Controller
@RequestMapping(value = "/contacts")
public class ContactsController {
    private final static Logger logger = LoggerFactory.getLogger(ContactsController.class);

    @Autowired
    private ContactRepository repository;

    @Autowired
    private CommandBus commandBus;

    @RequestMapping(method = RequestMethod.GET)
    public String list(Model model) {
        model.addAttribute("contacts", repository.findAllContacts());
        return "contacts/list";
    }

    @RequestMapping(value = "{identifier}", method = RequestMethod.GET)
    public String details(@PathVariable String identifier, Model model) {
        List<AddressEntry> addressesForContact = repository.findAllAddressesForContact(identifier);
        String name;
        if (addressesForContact.size() > 0) {
            name = addressesForContact.get(0).getName();
        } else {
            name = repository.loadContactDetails(identifier).getName();
        }
        model.addAttribute("addresses", addressesForContact);
        model.addAttribute("identifier", identifier);
        model.addAttribute("name", name);
        return "contacts/details";
    }

    @RequestMapping(value = "{identifier}/edit", method = RequestMethod.GET)
    public String formEdit(@PathVariable String identifier, Model model) {
        ContactEntry contactEntry = repository.loadContactDetails(identifier);
        model.addAttribute("contact", contactEntry);
        return "contacts/edit";
    }

    @RequestMapping(value = "{identifier}/edit", method = RequestMethod.POST)
    public String formEditSubmit(@ModelAttribute("contact") @Valid ContactEntry contact, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "contacts/edit";
        }
        ChangeContactNameCommand command = new ChangeContactNameCommand();
        command.setContactNewName(contact.getName());
        command.setContactId(contact.getIdentifier());

        FutureCallback<Void> voidFutureCallback = new FutureCallback<Void>();
        commandBus.dispatch(command, voidFutureCallback);
        ObjectError error;
        try {
            voidFutureCallback.get();
            return "redirect:/contacts";
        } catch (InterruptedException e) {
            logger.debug("Error while changing name of contact", e);
            error = new ObjectError("contact", "Please try again, something went wrong on the server");
        } catch (ExecutionException e) {
            logger.debug("Error while changing name of contact", e);
            if (e.getCause().getClass().equals(ContactNameAlreadyTakenException.class)) {
                error = new FieldError("contact", "name",
                        "The provided name \'" + contact.getName() + "\' already exists");
            } else {
                error = new ObjectError("contact",
                        "Something went wrong on the server that we did not expect: " + e.getMessage());
            }
        }
        bindingResult.addError(error);

        return "contacts/edit";

    }

    @RequestMapping(value = "new", method = RequestMethod.GET)
    public String formNew(Model model) {
        ContactEntry attributeValue = new ContactEntry();
        model.addAttribute("contact", attributeValue);
        return "contacts/new";
    }

    /**
     * If we submit a new contact, we want immediate feedback if the contact could be added. If it could not be added
     * we want an error. Therefore we use the Future callback mechanism as provide by Axon.
     *
     * @param contact       ContactEntry object that contains the entered data
     * @param bindingResult BindingResult containing information about the binding of the form data to the ContactEntry
     * @return String representing the name of the view to present.
     */
    @RequestMapping(value = "new", method = RequestMethod.POST)
    public String formNewSubmit(@ModelAttribute("contact") @Valid ContactEntry contact, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "contacts/new";
        }
        CreateContactCommand command = new CreateContactCommand();
        command.setNewContactName(contact.getName());

        FutureCallback<Void> voidFutureCallback = new FutureCallback<Void>();
        commandBus.dispatch(command, voidFutureCallback);
        ObjectError error;
        try {
            voidFutureCallback.get();
            return "redirect:/contacts";
        } catch (InterruptedException e) {
            logger.debug("Error while registering a new contact", e);
            error = new ObjectError("contact", "Please try again, something went wrong on the server");
        } catch (ExecutionException e) {
            logger.debug("Error while registering a new contact", e);
            if (e.getCause().getClass().equals(ContactNameAlreadyTakenException.class)) {
                error = new FieldError("contact", "name",
                        "The provided name \'" + contact.getName() + "\' already exists");
            } else {
                error = new ObjectError("contact",
                        "Something went wrong on the server that we did not expect: " + e.getMessage());
            }
        }
        bindingResult.addError(error);
        return "contacts/new";
    }

    @RequestMapping(value = "{identifier}/delete", method = RequestMethod.GET)
    public String formDelete(@PathVariable String identifier, Model model) {
        ContactEntry contactEntry = repository.loadContactDetails(identifier);
        model.addAttribute("contact", contactEntry);
        return "contacts/delete";
    }

    @RequestMapping(value = "{identifier}/delete", method = RequestMethod.POST)
    public String formDelete(@ModelAttribute("contact") ContactEntry contact, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            RemoveContactCommand command = new RemoveContactCommand();
            command.setContactId(contact.getIdentifier());
            commandBus.dispatch(command);

            return "redirect:/contacts";
        }
        return "contacts/delete";

    }

    @RequestMapping(value = "{identifier}/address/new", method = RequestMethod.GET)
    public String formNewAddress(@PathVariable String identifier, Model model) {
        ContactEntry contactEntry = repository.loadContactDetails(identifier);
        AddressEntry addressEntry = new AddressEntry();
        addressEntry.setIdentifier(contactEntry.getIdentifier());
        addressEntry.setName(contactEntry.getName());
        model.addAttribute("address", addressEntry);
        return "contacts/address";
    }

    @RequestMapping(value = "{identifier}/address/new", method = RequestMethod.POST)
    public String formNewAddressSubmit(@ModelAttribute("address") @Valid AddressEntry address, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            RegisterAddressCommand command = new RegisterAddressCommand();
            command.setAddressType(address.getAddressType());
            command.setCity(address.getCity());
            command.setContactId(address.getIdentifier());
            command.setStreetAndNumber(address.getStreetAndNumber());
            command.setZipCode(address.getZipCode());
            commandBus.dispatch(command);

            return "redirect:/contacts/" + address.getIdentifier();
        }
        return "contacts/address";
    }


    @RequestMapping(value = "{identifier}/address/delete/{addressType}", method = RequestMethod.GET)
    public String formDeleteAddress(@PathVariable String identifier, @PathVariable AddressType addressType, Model model) {
        ContactEntry contactEntry = repository.loadContactDetails(identifier);
        AddressEntry addressEntry = new AddressEntry();
        addressEntry.setIdentifier(contactEntry.getIdentifier());
        addressEntry.setName(contactEntry.getName());
        addressEntry.setAddressType(addressType);
        model.addAttribute("address", addressEntry);
        return "contacts/removeAddress";
    }

    @RequestMapping(value = "{identifier}/address/delete/{addressType}", method = RequestMethod.POST)
    public String formDeleteAddressSubmit(@ModelAttribute("address") AddressEntry address, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            RemoveAddressCommand command = new RemoveAddressCommand();
            command.setContactId(address.getIdentifier());
            command.setAddressType(address.getAddressType());
            commandBus.dispatch(command);

            return "redirect:/contacts/" + address.getIdentifier();
        }
        return "contacts/removeAddress";
    }
}
